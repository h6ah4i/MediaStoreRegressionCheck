package com.h6ah4i.example.mediastoreregchk

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.contentValuesOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MainActivity : AppCompatActivity() {
    private val testAlbumName = "Album 01"
    private val testArtistNameA = "Artist A" // should be 2 tracks
    private val testArtistNameB = "Artist B" // should be 3 tracks
    private val testArtistNameC = "Artist C" // should be 1 tracks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                if (Build.VERSION.SDK_INT <= 28) Manifest.permission.WRITE_EXTERNAL_STORAGE else null
            ).filterNotNull().toTypedArray(),
            REQUEST_CODE_PERMISSIONS
        )

        findViewById<Button>(R.id.buttonCopyAudioFiles).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                setupTestMedia(applicationContext)
            }
        }

        findViewById<Button>(R.id.buttonQueryMediaStore).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                var text = ""

                text += collectArtistInfo(applicationContext, testArtistNameA) + "\n\n"
                text += collectArtistInfo(applicationContext, testArtistNameB) + "\n\n"
                text += collectArtistInfo(applicationContext, testArtistNameC) + "\n\n"

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.textViewQueryResult).text = text
                }
            }
        }

        setTitle("${getString(R.string.app_name)} - API Level ${Build.VERSION.SDK_INT}")
    }

    private fun collectArtistInfo(context: Context, artistName: String): String {
        val contentResolver = context.contentResolver

        val artistId = contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Audio.Media.ARTIST_ID),
            "${MediaStore.Audio.Media.ARTIST} = ?", arrayOf(artistName),
            null
        )!!.use {
            it.moveToFirst()
            it.getLong(0)
        }


        val albums = contentResolver.query(
            MediaStore.Audio.Artists.Albums.getContentUri(MediaStore.VOLUME_EXTERNAL, artistId),
            arrayOf(
                MediaStore.Audio.Artists.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Artists.Albums.NUMBER_OF_SONGS_FOR_ARTIST
            ),
            null, null, null
        )!!.use {
                it.moveToFirst()
                """
                Albums count: ${it.count}
                NUMBER_OF_SONGS: ${if (it.count > 0) it.getInt(0) else 0}
                NUMBER_OF_SONGS_FOR_ARTIST: ${if (it.count > 0) it.getInt(1) else 0}
                """.trimIndent()
            }!!

        val tracks = contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE),
            "${MediaStore.Audio.Media.ARTIST_ID} = ?", arrayOf(artistId.toString()), null
        )!!.use {
            0.until(it.count).map { pos ->
                it.moveToPosition(pos)

                """
                ID: ${it.getLong(0)}, Album: ${it.getString(1)}, Title: ${it.getString(2)}
                """.trimIndent()
            }.joinToString("\n")
        }

        return  arrayOf(
            "** Artist: $artistName (id: $artistId) **",
            "",
            "[Album]",
            albums,
            "",
            "[Tracks]",
            tracks,
            "*********************").joinToString("\n")
    }

    private suspend fun setupTestMedia(context: Context) {
        val contentResolver = context.contentResolver
        val assetManager = context.assets
        val srcDirName = testAlbumName

        val srcFileNames = assetManager.list(srcDirName)!!

        if (Build.VERSION.SDK_INT >= 29) {
            cleanUpMediaStore(context)
            srcFileNames.forEach { filename ->
                val path = "${srcDirName}${File.separator}${filename}"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")

                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$srcDirName")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    contentValues
                )!!

                assetManager.open(path).use { src ->
                    contentResolver.openOutputStream(uri, "w")!!.use { dest -> src.copyTo(dest) }
                }

                contentResolver.update(
                    uri,
                    contentValuesOf(Pair(MediaStore.Audio.Media.IS_PENDING, 0)),
                    null,
                    null
                )
            }
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val srcFilePaths = srcFileNames.map { "${srcDirName}${File.separator}${it}" }
            val destFilePaths =  srcFilePaths.map { "${musicDir}${File.separator}${it}" }

            // Create parent dir
            val destDir = File(musicDir, srcDirName)
            destDir.mkdir()

            // Copy child files
            srcFilePaths.forEach { path ->
                assetManager.open(path).use { src ->
                    File(musicDir, path).outputStream().use { dest ->
                        src.copyTo(dest)
                    }
                }
            }

            // Ask MediaScanner to scan them
            destFilePaths.forEach {
                MediaScannerConnection.scanFile(context, arrayOf(it), arrayOf("audio/mpeg")) { path, uri ->
                    Log.d(TAG, "path: $path, uri: $uri")
                }
            }
        }
    }

    @RequiresApi(29)
    private suspend fun cleanUpMediaStore(context: Context) {
        val contentResolver = context.contentResolver

        contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.ALBUM} = ?",
            arrayOf(testAlbumName),
            null
        )?.use {
            while (it.moveToNext()) {
                try {
                    contentResolver.delete(
                        ContentUris.withAppendedId(
                            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                            it.getLong(
                                0
                            )
                        ),
                        null, null
                    )
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                        withContext(Dispatchers.Main) {
                            startIntentSenderForResult(e.userAction.actionIntent.intentSender,
                                REQUEST_CODE_DELETE_MEDIA, null, 0, 0,0 , null)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediaStoreRegChk"
        private const val REQUEST_CODE_PERMISSIONS = 1
        private const val REQUEST_CODE_DELETE_MEDIA = 2 // TODO
    }
}
