// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.customUsageCollectors.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jdesktop.swingx.util.OS;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class A11YUsagesCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    String activity = ScreenReader.isActive() ? "on" : "off";
    String os = OS.isWindows() ? "Windows" : OS.isLinux() ? "Linux" : OS.isMacOSX() ? "Mac" : "Unknown OS";
    return Collections.singleton(new UsageDescriptor(os + " " + activity, 1));
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.ui.a11y.screenreader");
  }
}

