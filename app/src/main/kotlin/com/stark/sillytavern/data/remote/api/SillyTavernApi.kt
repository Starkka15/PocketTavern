package com.stark.sillytavern.data.remote.api

import com.stark.sillytavern.data.remote.dto.st.*
import com.stark.sillytavern.data.remote.dto.st.LoginRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.Streaming

interface SillyTavernApi {

    // MultiUserMode login endpoint
    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): Response<Unit>

    @GET("csrf-token")
    suspend fun getCsrfToken(): CsrfTokenResponse

    @POST("api/settings/get")
    suspend fun getSettings(): SettingsResponse

    @POST("api/characters/all")
    suspend fun getAllCharacters(): List<CharacterDto>

    @POST("api/characters/get")
    suspend fun getCharacter(@Body request: GetCharacterRequest): CharacterDto

    @POST("api/characters/create")
    suspend fun createCharacter(@Body character: CreateCharacterRequest): Response<Unit>

    @POST("api/characters/edit")
    suspend fun editCharacter(@Body character: EditCharacterRequest): Response<Unit>

    @POST("api/characters/edit-attribute")
    suspend fun editCharacterAttribute(@Body request: EditCharacterAttributeRequest): Response<Unit>

    @Multipart
    @POST("api/characters/import")
    suspend fun importCharacter(
        @Part avatar: MultipartBody.Part,
        @Part("file_type") fileType: okhttp3.RequestBody
    ): Response<ResponseBody>

    @POST("api/characters/delete")
    suspend fun deleteCharacter(@Body request: DeleteCharacterRequest): Response<Unit>

    @POST("api/characters/chats")
    suspend fun getCharacterChats(@Body request: GetChatsRequest): List<ChatInfoDto>

    @POST("api/chats/get")
    suspend fun getChat(@Body request: GetChatRequest): List<ChatMessageDto>

    @POST("api/chats/save")
    suspend fun saveChat(@Body request: SaveChatRequest): Response<Unit>

    @POST("api/chats/delete")
    suspend fun deleteChat(@Body request: DeleteChatRequest): Response<Unit>

    @POST("api/backends/text-completions/generate")
    suspend fun generateTextCompletion(@Body request: TextCompletionRequest): TextCompletionResponse

    @Streaming
    @POST("api/backends/text-completions/generate")
    suspend fun generateTextCompletionStreaming(@Body request: TextCompletionRequest): ResponseBody

    @POST("api/backends/text-completions/abort")
    suspend fun abortTextCompletion(): Response<Unit>

    @GET("thumbnail")
    suspend fun getThumbnail(
        @Query("type") type: String = "avatar",
        @Query("file") file: String
    ): ResponseBody

    @POST("api/characters/export")
    suspend fun exportCharacter(@Body request: ExportCharacterRequest): ResponseBody

    // User Management endpoints
    @GET("api/users/me")
    suspend fun getCurrentUser(): UserInfoResponse

    @POST("api/users/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    @POST("api/users/logout")
    suspend fun logout(): Response<Unit>

    // Full Settings endpoints
    @POST("api/settings/get")
    @Headers("Content-Type: application/json")
    suspend fun getFullSettings(@Body body: Map<String, String>): FullSettingsResponse

    @POST("api/settings/save")
    suspend fun saveServerSettings(@Body settings: kotlinx.serialization.json.JsonObject): Response<Unit>

    // Preset Management endpoints
    @POST("api/presets/save")
    suspend fun savePreset(@Body request: SavePresetRequest): Response<Unit>

    @POST("api/presets/delete")
    suspend fun deletePreset(@Body request: DeletePresetRequest): Response<Unit>

    // Backend Status endpoints - get available models
    @POST("api/backends/text-completions/status")
    suspend fun getTextCompletionStatus(@Body request: TextCompletionStatusRequest): Response<BackendStatusResponse>

    @POST("api/backends/chat-completions/status")
    suspend fun getChatCompletionStatus(@Body request: ChatCompletionStatusRequest): Response<BackendStatusResponse>

    // World Info / Lorebook endpoints
    @POST("api/worldinfo/list")
    suspend fun getWorldInfoList(): List<WorldInfoListItem>

    @POST("api/worldinfo/get")
    suspend fun getWorldInfo(@Body request: GetWorldInfoRequest): WorldInfoResponse

    @Multipart
    @POST("api/worldinfo/import")
    suspend fun importLorebook(
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    // User Avatars / Personas endpoints
    @POST("api/avatars/get")
    suspend fun getUserAvatars(): List<String>

    @POST("api/avatars/delete")
    suspend fun deleteUserAvatar(@Body request: DeleteAvatarRequest): Response<Unit>

    @Multipart
    @POST("api/avatars/upload")
    suspend fun uploadUserAvatar(
        @Part avatar: MultipartBody.Part,
        @Part("overwrite_name") overwriteName: okhttp3.RequestBody? = null
    ): Response<UploadAvatarResponse>

    // Groups endpoints
    @POST("api/groups/all")
    suspend fun getAllGroups(): List<GroupDto>

    @POST("api/groups/create")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<GroupDto>

    @POST("api/chats/group/get")
    suspend fun getGroupChat(@Body request: GetGroupChatRequest): List<GroupChatMessageDto>

    @POST("api/chats/group/save")
    suspend fun saveGroupChat(@Body request: SaveGroupChatRequest): Response<Unit>
}
