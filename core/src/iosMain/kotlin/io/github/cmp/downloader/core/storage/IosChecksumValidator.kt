package io.github.cmp.downloader.core.storage

import io.github.cmp.downloader.core.model.ChecksumAlgorithm
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import platform.CoreCrypto.CC_MD5_CTX
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_MD5_Final
import platform.CoreCrypto.CC_MD5_Init
import platform.CoreCrypto.CC_MD5_Update
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

@OptIn(ExperimentalForeignApi::class)
class IosChecksumValidator : IChecksumValidator {
    override fun computeChecksum(filePath: String, algorithm: ChecksumAlgorithm): String {
        if (algorithm == ChecksumAlgorithm.NONE) return ""

        val file = fopen(filePath, "rb") ?: throw IllegalArgumentException("Cannot open file at $filePath")
        val bufferSize = 64 * 1024
        val buffer = ByteArray(bufferSize)

        return try {
            when (algorithm) {
                ChecksumAlgorithm.SHA256 -> computeSha256(file, buffer)
                ChecksumAlgorithm.MD5 -> computeMd5(file, buffer)
                ChecksumAlgorithm.NONE -> ""
            }
        } finally {
            fclose(file)
        }
    }

    private fun computeSha256(file: kotlinx.cinterop.CPointer<platform.posix.FILE>, buffer: ByteArray): String = memScoped {
        val ctx = alloc<CC_SHA256_CTX>()
        CC_SHA256_Init(ctx.ptr)

        var bytesRead: ULong
        while (true) {
            bytesRead = fread(buffer.refTo(0), 1u, buffer.size.toULong(), file)
            if (bytesRead == 0uL) break
            CC_SHA256_Update(ctx.ptr, buffer.refTo(0), bytesRead.toUInt())
        }

        val digest = allocArray<kotlinx.cinterop.UByteVar>(CC_SHA256_DIGEST_LENGTH)
        CC_SHA256_Final(digest, ctx.ptr)

        val bytes = digest.readBytes(CC_SHA256_DIGEST_LENGTH)
        val hexChars = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val intVal = b.toInt() and 0xFF
            val hex = intVal.toString(16).padStart(2, '0')
            hexChars.append(hex)
        }
        hexChars.toString()
    }

    private fun computeMd5(file: kotlinx.cinterop.CPointer<platform.posix.FILE>, buffer: ByteArray): String = memScoped {
        val ctx = alloc<CC_MD5_CTX>()
        CC_MD5_Init(ctx.ptr)

        var bytesRead: ULong
        while (true) {
            bytesRead = fread(buffer.refTo(0), 1u, buffer.size.toULong(), file)
            if (bytesRead == 0uL) break
            CC_MD5_Update(ctx.ptr, buffer.refTo(0), bytesRead.toUInt())
        }

        val digest = allocArray<kotlinx.cinterop.UByteVar>(CC_MD5_DIGEST_LENGTH)
        CC_MD5_Final(digest, ctx.ptr)

        val bytes = digest.readBytes(CC_MD5_DIGEST_LENGTH)
        val hexChars = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val intVal = b.toInt() and 0xFF
            val hex = intVal.toString(16).padStart(2, '0')
            hexChars.append(hex)
        }
        hexChars.toString()
    }
}

actual fun createChecksumValidator(): IChecksumValidator = IosChecksumValidator()
