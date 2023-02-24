/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.atom.utils

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset

object ScriptEnvUtils {
    private const val ENV_FILE = "result.log"
    private const val MULTILINE_FILE = "multiLine.log"
    private const val CONTEXT_FILE = "context.log"
    private const val QUALITY_GATEWAY_FILE = "gatewayValueFile.ini"
    private val keyRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")
    private val logger = LoggerFactory.getLogger(ScriptEnvUtils::class.java)

    fun cleanEnv(buildId: String, workspace: File) {
        cleanScriptEnv(workspace, getEnvFile(buildId))
        cleanScriptEnv(workspace, getDefaultEnvFile(buildId))
    }

    fun cleanContext(buildId: String, workspace: File) {
        cleanScriptEnv(workspace, getContextFile(buildId))
    }

    /*通过env文件，获取到环境变量map*/
    fun getEnv(buildId: String, workspace: File): Map<String, String> {
        return readScriptEnv(workspace, "$buildId-$ENV_FILE")
            .plus(readScriptEnv(workspace, getEnvFile(buildId)))
    }

    /*通过上下文文件，获取到上下文内容*/
    fun getContext(buildId: String, workspace: File): Map<String, String> {
        return readScriptContext(workspace, getContextFile(buildId))
    }

    /*限定文件名*/
    fun getEnvFile(buildId: String): String {
        val randomNum = ExecutorUtil.getThreadLocal()
        return "$buildId-$randomNum-$ENV_FILE"
    }

    /*获取多行内容*/
    fun getMultipleLines(buildId: String, workspace: File, charset: Charset = Charsets.UTF_8): List<String> {
        return readLines(workspace, getMultipleLineFile(buildId), charset)
    }

    /*限定文件名*/
    fun getMultipleLineFile(buildId: String): String {
        val randomNum = ExecutorUtil.getThreadLocal()
        return "$buildId-$randomNum-$MULTILINE_FILE"
    }

    /*限定文件名*/
    fun getContextFile(buildId: String): String {
        val randomNum = ExecutorUtil.getThreadLocal()
        return "$buildId-$randomNum-$CONTEXT_FILE"
    }

    /*限定文件名*/
    private fun getDefaultEnvFile(buildId: String): String {
        return "$buildId-$ENV_FILE"
    }

    /*清理逻辑*/
    fun cleanWhenEnd(buildId: String, workspace: File) {
        /*获取文件路径*/
        val defaultEnvFilePath = getDefaultEnvFile(buildId)
        val randomEnvFilePath = getEnvFile(buildId)
        val randomContextFilePath = getContextFile(buildId)
        val multiLineFilePath = getMultipleLineFile(buildId)
        /*清理文件*/
        deleteFile(multiLineFilePath, workspace)
        deleteFile(defaultEnvFilePath, workspace)
        deleteFile(randomEnvFilePath, workspace)
        deleteFile(randomContextFilePath, workspace)
        /*销毁线程临时变量数据*/
        ExecutorUtil.removeThreadLocal()
    }

    /*清理文件逻辑*/
    private fun deleteFile(filePath: String, workspace: File) {
        val defaultFile = File(workspace, filePath)
        if (defaultFile.exists()) {
            defaultFile.delete()
        }
    }

    fun getQualityGatewayEnvFile() = QUALITY_GATEWAY_FILE

    /*清理文件*/
    private fun cleanScriptEnv(workspace: File, file: String) {
        val scriptFile = File(workspace, file)
        if (scriptFile.exists()) {
            scriptFile.delete()
        }
        if (!scriptFile.createNewFile()) {
            logger.warn("Fail to create the file - (${scriptFile.absolutePath})")
        } else {
            scriptFile.deleteOnExit()
        }
    }

    /*读取env文件*/
    private fun readScriptEnv(workspace: File, file: String): Map<String, String> {
        val f = File(workspace, file)
        if (!f.exists() || f.isDirectory) {
            return mapOf()
        }
        /*读取文件内容*/
        val lines = f.readLines()
        return if (lines.isEmpty()) {
            mapOf()
        } else {
            // KEY-VALUE 格式读取
            lines.filter { it.contains("=") }.map {
                val split = it.split("=", ignoreCase = false, limit = 2)
                split[0].trim() to split[1].trim()
            }.filter {
                // #3453 保存时再次校验key的合法性
                keyRegex.matches(it.first)
            }.toMap()
        }
    }

    private fun readLines(workspace: File, file: String, charset: Charset = Charsets.UTF_8): List<String> {
        val f = File(workspace, file)
        /*不存在或是文件夹则返回空。非预期情况*/
        if (!f.exists() || f.isDirectory) {
            return emptyList()
        }
        printFileCharSet(workspace, file)
        return f.readLines(charset)
    }

    private fun printFileCharSet(workspace: File, fileName: String) {
        val file = File(workspace, fileName)
        val inStream: InputStream = FileInputStream(file)
        val b = ByteArray(3)
        inStream.read(b)
        inStream.close()
        if (b[0].toInt() == -17 && b[1].toInt() == -69 && b[2].toInt() == -65)
            logger.debug(file.name + "is UTF-8")
        else logger.debug(
            file.name + "is GBK ,etc."
        )
    }

    /*读取上下文文件*/
    private fun readScriptContext(workspace: File, file: String): Map<String, String> {
        val f = File(workspace, file)
        if (!f.exists() || f.isDirectory) {
            return mapOf()
        }

        /*读取文件内容*/
        val lines = f.readLines()
        return if (lines.isEmpty()) {
            mapOf()
        } else {
            // KEY-VALUE
            lines.filter { it.contains("=") }.map {
                val split = it.split("=", ignoreCase = false, limit = 2)
                split[0].trim() to split[1].trim()
            }.toMap()
        }
    }
}
