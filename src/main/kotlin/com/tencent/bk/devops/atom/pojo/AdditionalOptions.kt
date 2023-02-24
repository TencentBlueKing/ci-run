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

package com.tencent.bk.devops.atom.pojo

import com.tencent.bk.devops.atom.enums.OSType
import com.tencent.bk.devops.atom.exception.AtomException

data class AdditionalOptions(
    var shell: ShellType
) {
    constructor(shell: String) : this(ShellType.BASH) {
        /*用户没有配置脚本类型就按照系统类型默认选择*/
        this.shell = if (shell.isNullOrBlank()) {
            ShellType.get(AgentEnv.getOS())
        } else {
            ShellType.get(shell)
        }
    }
}

enum class ShellType(val shellName: String) {
    /*bash*/
    BASH("bash"),

    /*cmd*/
    CMD("cmd"),

    /*powershell*/
    POWERSHELL_CORE("pwsh"),

    /*powershell*/
    POWERSHELL_DESKTOP("powershell"),

    /*python*/
    PYTHON("python"),

    /*sh命令*/
    SH("sh"),

    /*windows 执行 bash*/
    WIN_BASH("win_bash"),

    /*按系统默认*/
    AUTO("auto");

    companion object {
        fun get(value: String): ShellType {
            if (value == AUTO.shellName) {
                return get(AgentEnv.getOS())
            }
            if (value == BASH.shellName && AgentEnv.getOS() == OSType.WINDOWS) {
                return WIN_BASH
            }
            values().forEach {
                if (value == it.shellName) return it
            }
            throw AtomException(
                "The current system(${AgentEnv.getOS()}) not support $value yet"
            )
        }

        fun get(value: OSType): ShellType {
            return when {
                value == OSType.WINDOWS -> CMD
                value == OSType.LINUX -> BASH
                else -> BASH
            }
        }
    }
}
