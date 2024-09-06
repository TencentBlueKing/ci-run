/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.tencent.bk.devops.atom.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream


abstract class RunOutputStream(private val chartSet: String) : OutputStream() {
    /** the internal buffer  */
    private val buffer = ByteArrayOutputStream(
            INTIAL_SIZE)

    private var skip = false

    private var lineCount = 0

    /**
     * Write the data to the buffer and flush the buffer, if a line separator is
     * detected.
     *
     * @param cc data to log (byte).
     * @see OutputStream.write
     */
    @Throws(IOException::class)
    override fun write(cc: Int) {
        val c = cc.toByte()
        if (c == '\n'.code.toByte() || c == '\r'.code.toByte()) {
            if (!skip) {
                processBuffer()
            }
        } else {
            buffer.write(cc)
        }
        skip = c == '\r'.code.toByte()
    }

    /**
     * Flush this log stream.
     *
     * @see OutputStream.flush
     */
    override fun flush() {
        if (buffer.size() > 0) {
            processBuffer()
        }
    }

    /**
     * Writes all remaining data from the buffer.
     *
     * @see OutputStream.close
     */
    @Throws(IOException::class)
    override fun close() {
        if (buffer.size() > 0) {
            processBuffer()
        }
        super.close()
    }

    /**
     * Write a block of characters to the output stream
     *
     * @param b the array containing the data
     * @param off the offset into the array where data starts
     * @param len the length of block
     * @throws IOException if the data cannot be written into the stream.
     * @see OutputStream.write
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        // find the line breaks and pass other chars through in blocks
        var offset = off
        var blockStartOffset = offset
        var remaining = len
        while (remaining > 0) {
            while (remaining > 0 && b[offset].toInt() != LF && b[offset].toInt() != CR) {
                offset++
                remaining--
            }
            // either end of buffer or a line separator char
            val blockLength = offset - blockStartOffset
            if (blockLength > 0) {
                buffer.write(b, blockStartOffset, blockLength)
            }
            while (remaining > 0 && (b[offset].toInt() == LF || b[offset].toInt() == CR)) {
                write(b[offset].toInt())
                offset++
                remaining--
            }
            blockStartOffset = offset
        }
    }

    /**
     * Converts the buffer to a string and sends it to `processLine`.
     */
    protected open fun processBuffer() {
        processLine(buffer.toString(chartSet), lineCount++)
        buffer.reset()
    }

    /**
     * Logs a line to the log system of the user.
     *
     * @param line
     * the line to log.
     */
    protected abstract fun processLine(line: String, lineCount: Int)

    companion object {
        /** Initial buffer size.  */
        private const val INTIAL_SIZE = 1024

        /** Carriage return  */
        private const val CR = 0x0d

        /** Linefeed  */
        private const val LF = 0x0a
    }
}
