/*
 * Copyright (c) 2021 The StreamX Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.streamxhub.streamx.flink.packer.docker

import com.streamxhub.streamx.common.fs.LfsOperator
import com.streamxhub.streamx.common.util.HadoopConfigUtils
import com.streamxhub.streamx.common.util.HadoopConfigUtils.{HADOOP_CLIENT_CONF_FILES, HIVE_CLIENT_CONF_FILES}

import java.io.File
import java.nio.file.Paths
import javax.annotation.Nullable

/**
 * flink-hadoop integration docker image template.
 *
 * @author Al-assad
 * @param workspacePath      Path of dockerfile workspace, it should be a directory.
 * @param flinkBaseImage     Flink base docker image name, see https://hub.docker.com/_/flink.
 * @param flinkMainJarPath   Path of flink job main jar which would copy to $FLINK_HOME/usrlib/
 * @param flinkExtraLibPaths Path of additional flink lib path which would copy to $FLINK_HOME/lib/
 * @param hadoopConfDirPath  Path of hadoop conf directory.
 * @param hiveConfDirPath    Path of hive conf directory.
 */
case class FlinkHadoopDockerfileTemplate(workspacePath: String,
                                         flinkBaseImage: String,
                                         flinkMainJarPath: String,
                                         flinkExtraLibPaths: Set[String],
                                         @Nullable hadoopConfDirPath: String,
                                         @Nullable hiveConfDirPath: String) extends FlinkDockerfileTemplateTrait {

  val hadoopConfDir: String = workspace.relativize(Paths.get(Option(hadoopConfDirPath).getOrElse(""))).toString

  val hiveConfDir: String = workspace.relativize(Paths.get(Option(hiveConfDirPath).getOrElse(""))).toString

  /**
   * offer content of DockerFile
   */
  override def offerDockerfileContent: String = {
    var dockerfile =
      s"""FROM $flinkBaseImage
         |RUN mkdir -p $FLINK_HOME/usrlib
         |COPY $mainJarName $FLINK_HOME/usrlib/$mainJarName
         |COPY $extraLibName $FLINK_HOME/lib/
         |""".stripMargin
    if (hadoopConfDir.nonEmpty) dockerfile +=
      s"""COPY $hadoopConfDir /opt/hadoop-conf
         |ENV HADOOP_CONF_DIR /opt/hadoop-conf
         |""".stripMargin
    if (hiveConfDir.nonEmpty) dockerfile +=
      s"""COPY $hiveConfDir /opt/hive-conf
         |ENV HIVE_CONF_DIR /opt/hive-conf
         |""".stripMargin
    dockerfile
  }

}

object FlinkHadoopDockerfileTemplate {

  /**
   * Use relevant system variables as the value of hadoopConfDirPath, hiveConfDirPath.
   */
  def fromSystemHadoopConf(workspacePath: String,
                           flinkBaseImage: String,
                           flinkMainJarPath: String,
                           flinkExtraLibPaths: Set[String],
                           optimizeHadoopConf: Boolean = true): FlinkHadoopDockerfileTemplate = {
    // get hadoop and hive config directory from system and copy to workspacePath
    val hadoopConfDir = HadoopConfigUtils.getSystemHadoopConfDir match {
      case hadoopConf if !LfsOperator.exists(hadoopConf) => ""
      case hadoopConf =>
        val dstDir = s"${workspacePath}/hadoop-conf"
        LfsOperator.mkCleanDirs(dstDir)
        LfsOperator.copyDir(hadoopConf, dstDir)
        dstDir
    }
    val hiveConfDir = HadoopConfigUtils.getSystemHiveConfDir match {
      case hiveConf if !LfsOperator.exists(hiveConf) => ""
      case hiveConf =>
        val dstDir = s"${workspacePath}/hive-conf"
        LfsOperator.mkCleanDirs(dstDir)
        LfsOperator.copyDir(hiveConf, dstDir)
        dstDir
    }
    // optimize hadoop and hive config content
    if (optimizeHadoopConf) {
      if (hadoopConfDir.nonEmpty) HadoopConfigUtils.batchReplaceHostWithIP(new File(hadoopConfDir), HADOOP_CLIENT_CONF_FILES)
      if (hiveConfDir.nonEmpty) HadoopConfigUtils.batchReplaceHostWithIP(new File(hiveConfDir), HIVE_CLIENT_CONF_FILES)
    }
    FlinkHadoopDockerfileTemplate(workspacePath, flinkBaseImage, flinkMainJarPath, flinkExtraLibPaths,
      hadoopConfDir, hiveConfDir)
  }

}
