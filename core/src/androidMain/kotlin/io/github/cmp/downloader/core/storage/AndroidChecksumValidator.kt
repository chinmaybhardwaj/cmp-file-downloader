package io.github.cmp.downloader.core.storage

import io.github.cmp.downloader.core.model.ChecksumAlgorithm
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class AndroidChecksumValidator : IChecksumValidator {
    override fun computeChecksum(filePath: String, algorithm: ChecksumAlgorithm): String {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Cannot compute checksum: file does not exist at $filePath")
        }

        val algorithmName = when (algorithm) {
            ChecksumAlgorithm.SHA256 -> "SHA-256"
            ChecksumAlgorithm.MD5 -> "MD5"
            ChecksumAlgorithm.NONE -> return ""
        }

        val digest = MessageDigest.getInstance(algorithmName)
        val buffer = ByteArray(64 * 1024) // 64 KB buffer for streaming

        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val hashBytes = digest.digest()
        val hexChars = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            val hex = (b.toInt() and 0xFF).toString(16)
            if (hex.length == 1) hexChars.append('0')
            hexChars.append(hex)
        }
        return hexChars.toString()
    }
}

actual fun createChecksumValidator(): IChecksumValidator = AndroidChecksumValidator()
