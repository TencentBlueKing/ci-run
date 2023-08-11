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
import sun.security.action.GetPropertyAction
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.security.AccessController

object CommonUtil {

    private val logger = LoggerFactory.getLogger(CommonUtil::class.java)

    /*统一打日志，debug信息用于问题排查*/
    fun printTempFileInfo(file: File, charset: Charset) {
        logger.debug("--------file(${file.name}) debug info-------------")
        logger.debug("absolutePath: ${file.absolutePath}")
        logger.debug("Size: ${file.length()}")
        logger.debug("canExecute/canRead/canWrite: ${file.canExecute()}/${file.canRead()}/${file.canWrite()}")
        logger.debug("--------file debug info end-------------")
        logger.debug("--------user script start-------------")
        file.readLines(charset).forEach {
            logger.debug(it)
        }
        logger.debug("--------user script end-------------")
    }

    fun escapeString(str: String): String {
        val builder = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }

    fun getTmpDir(): Path = Paths.get(AccessController.doPrivileged(GetPropertyAction("user.dir")))
}
