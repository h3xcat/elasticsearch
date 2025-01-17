/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.internal.test;

import org.elasticsearch.gradle.internal.info.GlobalBuildInfoPlugin;
import org.elasticsearch.gradle.util.GradleUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import static org.elasticsearch.gradle.internal.util.ParamsUtils.loadBuildParams;

public class InternalClusterTestPlugin implements Plugin<Project> {

    public static final String SOURCE_SET_NAME = "internalClusterTest";

    @Override
    public void apply(Project project) {
        project.getRootProject().getPlugins().apply(GlobalBuildInfoPlugin.class);
        var buildParams = loadBuildParams(project).get();

        TaskProvider<Test> internalClusterTest = GradleUtils.addTestSourceSet(project, SOURCE_SET_NAME);
        internalClusterTest.configure(task -> {
            // Set GC options to mirror defaults in jvm.options
            if (buildParams.getRuntimeJavaVersion().get().compareTo(JavaVersion.VERSION_14) < 0) {
                task.jvmArgs("-XX:+UseConcMarkSweepGC", "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly");
            } else {
                task.jvmArgs("-XX:+UseG1GC");
            }
        });

        // TODO: fix usages of IT tests depending on Tests methods so this extension is not necessary
        GradleUtils.extendSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME, SOURCE_SET_NAME);
    }
}
