package com.tencent.bk.devops.atom.pojo

import com.tencent.bk.devops.atom.enums.OSType
import java.util.Locale
import org.slf4j.LoggerFactory

object AgentEnv {

    private val logger = LoggerFactory.getLogger(AgentEnv::class.java)

    private var os: OSType? = null

    /*获取系统类型*/
    fun getOS(): OSType {
        if (os == null) {
            synchronized(this) {
                if (os == null) {
                    val os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
                    logger.info("Get the os name - ($os)")
                    this.os = if (os.indexOf(string = "mac") >= 0 || os.indexOf("darwin") >= 0) {
                        OSType.MAC_OS
                    } else if (os.indexOf("win") >= 0) {
                        OSType.WINDOWS
                    } else if (os.indexOf("nux") >= 0) {
                        OSType.LINUX
                    } else {
                        OSType.OTHER
                    }
                }
            }
        }
        return os!!
    }
}
