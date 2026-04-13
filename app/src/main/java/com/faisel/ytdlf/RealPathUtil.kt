package com.faisel.ytdlf

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

object RealPathUtil {
    fun getRealPath(context: Context, uri: Uri): String? {
        if (DocumentsContract.isTreeUri(uri)) {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val split = documentId.split(":")
            val type = split[0]
            val path = if (split.size > 1) split[1] else ""

            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + path
            } else {
                return "/storage/" + type + "/" + path
            }
        }

        return uri.path
    }
}