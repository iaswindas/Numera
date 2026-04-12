package com.numera.auth.dto

data class SsoConfigRequest(
    val providerType: String = "OIDC",
    val providerName: String = "",
    val clientId: String? = null,
    val clientSecret: String? = null,
    val issuerUri: String? = null,
    val authorizationUri: String? = null,
    val tokenUri: String? = null,
    val userinfoUri: String? = null,
    val jwksUri: String? = null,
    val samlMetadataUrl: String? = null,
    val samlEntityId: String? = null,
    val samlAcsUrl: String? = null,
)

data class SsoConfigResponse(
    val id: String,
    val providerType: String,
    val providerName: String,
    val issuerUri: String?,
    val isActive: Boolean,
)

data class SsoCallbackRequest(
    val provider: String = "",
    val code: String = "",
    val state: String? = null,
    val redirectUri: String = "",
)
