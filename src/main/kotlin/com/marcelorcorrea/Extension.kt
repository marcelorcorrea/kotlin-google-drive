package com.marcelorcorrea

import java.io.File

fun com.google.api.client.http.HttpResponse.download(filePath: String): File {
    val localFile = File(filePath)
    content.use { input ->
        localFile.outputStream().use {
            input.copyTo(it)
        }
    }
    return localFile
}