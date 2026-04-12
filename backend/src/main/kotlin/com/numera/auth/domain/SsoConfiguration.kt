package com.numera.auth.domain

import com.numera.shared.domain.TenantAwareEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "sso_configurations")
class SsoConfiguration : TenantAwareEntity() {
    @Column(name = "provider_type", nullable = false)
    var providerType: String = "OIDC"

    @Column(name = "provider_name", nullable = false)
    var providerName: String = ""

    @Column(name = "client_id")
    var clientId: String? = null

    @Column(name = "client_secret")
    var clientSecret: String? = null

    @Column(name = "issuer_uri")
    var issuerUri: String? = null

    @Column(name = "authorization_uri")
    var authorizationUri: String? = null

    @Column(name = "token_uri")
    var tokenUri: String? = null

    @Column(name = "userinfo_uri")
    var userinfoUri: String? = null

    @Column(name = "jwks_uri")
    var jwksUri: String? = null

    @Column(name = "saml_metadata_url")
    var samlMetadataUrl: String? = null

    @Column(name = "saml_entity_id")
    var samlEntityId: String? = null

    @Column(name = "saml_acs_url")
    var samlAcsUrl: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
}
