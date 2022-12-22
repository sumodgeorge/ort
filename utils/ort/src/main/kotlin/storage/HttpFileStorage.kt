/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.utils.ort.storage

import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.execute
import javax.sql.ConnectionPoolDataSource

private const val HTTP_CLIENT_CONNECT_TIMEOUT_IN_SECONDS = 30L

/**
 * A [FileStorage] that stores files on an HTTP server.
 */
class HttpFileStorage(
    /**
     * The URL to store files at.
     */
    val url: String,

    /**
     * The query string that is appended to the combination of the URL and some additional path. Some storages process
     * authentication via parameters that are within the final URL, so certain credentials can be stored in this
     * query, e.g, "?user=standard&pwd=123". Thus, the final URL could be
     * "https://example.com/storage/path?user=standard&pwd=123".
     */
    val query: String = "",

    /**
     * Custom headers that are added to all HTTP requests.
     */
    private val headers: Map<String, String> = emptyMap(),

    /**
     * The max age of an HTTP cache entry in seconds. Defaults to 0 which always validates the cached response with the
     * remote server.
     */
    private val cacheMaxAgeInSeconds: Int = 1
) : FileStorage {
    companion object : Logging

    private val httpClient by lazy {
        OkHttpClientHelper.buildClient {
            connectTimeout(Duration.ofSeconds(HTTP_CLIENT_CONNECT_TIMEOUT_IN_SECONDS))
            retryOnConnectionFailure(true)
            connectionPool(ConnectionPool(5, 120, TimeUnit.SECONDS))
        }
    }

    override fun exists(path: String): Boolean {
        dmesg("exists: " + path)
        val request = Request.Builder()
            .headers(headers.toHeaders())
            .cacheControl(CacheControl.Builder().maxAge(cacheMaxAgeInSeconds, TimeUnit.SECONDS).build())
            .head()
            .url(urlForPath(path))
            .build()

        val response =  try {
            httpClient.execute(request)
        } catch (e: Exception) {
            dmesg("exists: $path")
            dmesg("url: " + request.url)
            dmesg("exception: " + e.stackTraceToString())

            throw e
        }

        return response.isSuccessful
    }

    override fun read(path: String): InputStream {
        dmesg("read: " + path)
        val request = Request.Builder()
            .headers(headers.toHeaders())
            .cacheControl(CacheControl.Builder().maxAge(cacheMaxAgeInSeconds, TimeUnit.SECONDS).build())
            .get()
            .url(urlForPath(path))
            .build()

        logger.debug { "Reading file from storage: ${request.url}" }

        val response =  try {
            httpClient.execute(request)
        } catch (e: Exception) {
            dmesg("read: $path")
            dmesg("url: " + request.url)
            dmesg("exception: " + e.stackTraceToString())

            throw e
        }

        if (response.isSuccessful) {
            response.body?.let { body ->
                return body.byteStream()
            }

            response.close()
            throw IOException("The response body must not be null.")
        }

        response.close()
        throw IOException("Could not read from '${request.url}': ${response.code} - ${response.message}")
    }

    override fun write(path: String, inputStream: InputStream) {
        dmesg("write: " + path)
        inputStream.use {
            val request = Request.Builder()
                .headers(headers.toHeaders())
                .put(it.readBytes().toRequestBody())
                .url(urlForPath(path))
                .build()

            logger.debug { "Writing file to storage: ${request.url}" }

            val response =  try {
                httpClient.execute(request)
            } catch (e: Exception) {
                dmesg("write: $path")
                dmesg("url: " + request.url)
                dmesg("exception: " + e.stackTraceToString())

                throw e
            }

            response.use {
                if (!it.isSuccessful) {
                    throw IOException(
                        "Could not store file at '${request.url}': ${it.code} - ${it.message}"
                    )
                }
            }
        }
    }

    private fun urlForPath(path: String) = "$url/$path$query"
}

private fun dmesg(str: String) {
    println("XXX " + str)
}
