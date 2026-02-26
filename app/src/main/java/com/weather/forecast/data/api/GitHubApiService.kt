package com.weather.forecast.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * GitHub Releases API Service
 *
 * Digunakan untuk memeriksa versi terbaru aplikasi yang tersedia
 * di GitHub Releases. Endpoint ini bersifat publik dan tidak memerlukan
 * token autentikasi untuk repositori publik.
 *
 * Base URL: https://api.github.com/
 *
 * Flow pengecekan update:
 * 1. Panggil [getLatestRelease] untuk mendapatkan release terbaru
 * 2. Bandingkan tag_name dengan versionName yang terinstall
 * 3. Jika lebih baru, download APK dari assets[0].browser_download_url
 *
 * @see com.weather.forecast.update.AppUpdateManager
 */
interface GitHubApiService {

    /**
     * Ambil data release terbaru dari repository GitHub
     *
     * @param owner Pemilik/organisasi repository (e.g. "eltfd")
     * @param repo Nama repository (e.g. "Cuacaku")
     * @return [GitHubRelease] berisi tag, nama, body, dan daftar asset (APK)
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease

    companion object {
        const val BASE_URL = "https://api.github.com/"

        /** GitHub owner / organisasi */
        const val REPO_OWNER = "eltfd"

        /** Nama repository */
        const val REPO_NAME = "Cuacaku"
    }
}

/**
 * Model response dari GitHub Release API
 *
 * Contoh JSON:
 * ```json
 * {
 *   "tag_name": "v1.1.0",
 *   "name": "Release v1.1.0",
 *   "body": "Changelog...",
 *   "assets": [
 *     {
 *       "name": "app-release.apk",
 *       "browser_download_url": "https://github.com/..."
 *     }
 *   ]
 * }
 * ```
 */
data class GitHubRelease(
    /** Tag version, misalnya "v1.1.0" */
    @SerializedName("tag_name")
    val tagName: String,

    /** Nama release */
    val name: String?,

    /** Deskripsi / changelog release */
    val body: String?,

    /** Daftar file asset (APK, dll.) */
    val assets: List<GitHubAsset>
)

/**
 * Model asset dalam GitHub Release
 *
 * Setiap release bisa memiliki beberapa asset.
 * Aplikasi mencari asset dengan nama "app-release.apk".
 */
data class GitHubAsset(
    /** Nama file, misalnya "app-release.apk" */
    val name: String,

    /** URL download langsung yang bisa diakses tanpa autentikasi */
    @SerializedName("browser_download_url")
    val downloadUrl: String,

    /** Ukuran file dalam bytes */
    val size: Long
)
