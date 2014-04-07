/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleTypeWithLazyComponents;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMMA;

/**
 * @author ilyas
 */
public class GrListOrMapImpl extends GrExpressionImpl implements GrListOrMap {
  private static final TokenSet MAP_LITERAL_TOKEN_SET = TokenSet.create(GroovyElementTypes.NAMED_ARGUMENT, GroovyTokenTypes.mCOLON);
  private static final Function<GrListOrMapImpl, PsiType> TYPES_CALCULATOR = new MyTypesCalculator();

  private PsiReference myLiteralReference = new LiteralConstructorReference(this);

  public GrListOrMapImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitListOrMap(this);
  }

  public String toString() {
    return "Generalized list";
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (getInitializers().length == 0) {
      return super.addInternal(first, last, getNode().getFirstChildNode(), false);
    }
    final ASTNode lastChild = getNode().getLastChildNode();
    getNode().addLeaf(mCOMMA, ",", lastChild);
    return super.addInternal(first, last, lastChild.getTreePrev(), false);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi instanceof GrExpression || psi instanceof GrNamedArgument) {
      PsiElement prev = PsiUtil.getPrevNonSpace(psi);
      PsiElement next = PsiUtil.getNextNonSpace(psi);
      if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == mCOMMA) {
        super.deleteChildInternal(prev.getNode());
      }
      else if (next instanceof LeafPsiElement && next.getNode() != null && next.getNode().getElementType() == mCOMMA) {
        super.deleteChildInternal(next.getNode());
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
  }

  @Override
  public boolean isMap() {
    return findChildByType(MAP_LITERAL_TOKEN_SET) != null;
  }

  @Override
  public PsiElement getLBrack() {
    return findChildByType(GroovyTokenTypes.mLBRACK);
  }

  @Override
  public PsiElement getRBrack() {
    return findChildByType(GroovyTokenTypes.mRBRACK);
  }

  @Override
  @NotNull
  public GrExpression[] getInitializers() {
    List<GrExpression> result = ContainerUtil.newArrayList();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) {
        result.add((GrExpression)cur);
      }
    }
    return result.toArray(new GrExpression[result.size()]);
  }

  @Override
  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    List<GrNamedArgument> result = new ArrayList<GrNamedArgument>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrNamedArgument) result.add((GrNamedArgument)cur);
    }
    return result.toArray(new GrNamedArgument[result.size()]);
  }

  @Override
  public GrNamedArgument findNamedArgument(@NotNull String label) {
    return PsiImplUtil.findNamedArgument(this, label);
  }

  @Override
  public PsiReference getReference() {
    return myLiteralReference;
  }

  private static class MyTypesCalculator implements Function<GrListOrMapImpl, PsiType> {
    @Override
    @Nullable
    public PsiType fun(GrListOrMapImpl listOrMap) {
      final GlobalSearchScope scope = listOrMap.getResolveScope();
      if (listOrMap.isMap()) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(listOrMap.getProject());
        return inferMapInitializerType(listOrMap, facade, scope);
      }

      PsiElement parent = listOrMap.getParent();
      if (parent.getParent() instanceof GrVariableDeclaration) {
        GrTypeElement typeElement = ((GrVariableDeclaration)parent.getParent()).getTypeElementGroovy();
        if (typeElement != null) {
          PsiType declaredType = typeElement.getType();
          if (declaredType instanceof PsiArrayType) return declaredType;
        }
      }

      return getTupleType(listOrMap.getInitializers(), listOrMap);
    }

    @Nullable
    private static PsiClassType inferMapInitializerType(GrListOrMapImpl listOrMap, JavaPsiFacade facade, GlobalSearchScope scope) {
      final HashMap<String, PsiType> stringEntries = new HashMap<String, PsiType>();
      final ArrayList<Pair<PsiType, PsiType>> otherEntries = new ArrayList<Pair<PsiType, PsiType>>();
      GrNamedArgument[] namedArgs = listOrMap.getNamedArguments();

      if (namedArgs.length == 0) {
        PsiType lType = PsiImplUtil.inferExpectedTypeForDiamond(listOrMap);

        if (lType instanceof PsiClassType && InheritanceUtil.isInheritor(lType, CommonClassNames.JAVA_UTIL_MAP)) {
          PsiClass hashMap = facade.findClass(GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP, scope);
          if (hashMap == null) hashMap = facade.findClass(CommonClassNames.JAVA_UTIL_MAP, scope);
          if (hashMap != null) {
            PsiSubstitutor mapSubstitutor = PsiSubstitutor.EMPTY.
              put(hashMap.getTypeParameters()[0], com.intellij.psi.util.PsiUtil.substituteTypeParameter(lType,  CommonClassNames.JAVA_UTIL_MAP, 0, false)).
              put(hashMap.getTypeParameters()[1], com.intellij.psi.util.PsiUtil.substituteTypeParameter(lType,  CommonClassNames.JAVA_UTIL_MAP, 1, false));
            return facade.getElementFactory().createType(hashMap, mapSubstitutor);
          }
        }
      }

      for (GrNamedArgument namedArg : namedArgs) {
        final GrArgumentLabel label = namedArg.getLabel();
        final GrExpression expression = namedArg.getExpression();
        if (label == null || expression == null) {
          continue;
        }

        final String name = label.getName();
        if (name != null) {
          stringEntries.put(name, expression.getType());
        } else {
          otherEntries.add(Pair.create(label.getLabelType(), expression.getType()));
        }
      }

      return GrMapType.create(facade, scope, stringEntries, otherEntries);
    }


    private static PsiClassType getTupleType(final GrExpression[] initializers, GrListOrMap listOrMap) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(listOrMap.getProject());
      GlobalSearchScope scope = listOrMap.getResolveScope();

      if (initializers.length == 0) {
        PsiType lType = PsiImplUtil.inferExpectedTypeForDiamond(listOrMap);

        if (lType instanceof PsiClassType && InheritanceUtil.isInheritor(lType, CommonClassNames.JAVA_UTIL_LIST)) {
          PsiClass arrayList = facade.findClass(CommonClassNames.JAVA_UTIL_ARRAY_LIST, scope);
          if (arrayList == null) arrayList = facade.findClass(CommonClassNames.JAVA_UTIL_LIST, scope);
          if (arrayList != null) {
            PsiSubstitutor arrayListSubstitutor = PsiSubstitutor.EMPTY.
              put(arrayList.getTypeParameters()[0], com.intellij.psi.util.PsiUtil.substituteTypeParameter(lType, CommonClassNames.JAVA_UTIL_LIST, 0, false));
            return facade.getElementFactory().createType(arrayList, arrayListSubstitutor);
          }
        }
      }

      return new GrTupleTypeWithLazyComponents(scope, facade) {
        @Override
        protected PsiType[] inferComponents() {
          return ContainerUtil.map(initializers, new Function<GrExpression, PsiType>() {
            @Override
            public PsiType fun(GrExpression expression) {
              return expression.getType();
            }
          }, new PsiType[initializers.length]);
        }

        @Override
        public boolean isValid() {
          for (GrExpression initializer : initializers) {
            if (!initializer.isValid()) return false;
          }
          return true;
        }
      };
    }

  }
}
