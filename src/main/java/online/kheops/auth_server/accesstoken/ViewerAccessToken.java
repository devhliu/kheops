package online.kheops.auth_server.accesstoken;

import online.kheops.auth_server.entity.User;
import online.kheops.auth_server.principal.KheopsPrincipal;
import online.kheops.auth_server.principal.ViewerPrincipal;
import online.kheops.auth_server.util.Consts;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.ServletContext;
import java.io.StringReader;
import java.util.Optional;

public final class ViewerAccessToken implements AccessToken {

    private final JsonObject jwe;
    private final AccessToken accessToken;

    static final class Builder {
        private final ServletContext servletContext;

        private Builder(ServletContext servletContext) {
            this.servletContext = servletContext;
        }

        ViewerAccessToken build(String assertionToken)
                throws AccessTokenVerificationException {

            try(JsonReader jsonReader = Json.createReader(new StringReader(assertionToken))) {
                JsonObject jwe = jsonReader.readObject();
                return new ViewerAccessToken(servletContext, jwe);
            }
        }
    }

    static Builder getBuilder(ServletContext servletContext) { return new Builder(servletContext); }

    private ViewerAccessToken(ServletContext servletContext, JsonObject jwe)
            throws AccessTokenVerificationException {

        this.jwe = jwe;

        this.accessToken = AccessTokenVerifier.authenticateAccessToken(servletContext, jwe.getString(Consts.JWE.TOKEN));
    }

    public AccessToken getAccessToken() {
        return accessToken;
    }

    public boolean isInbox() {
        return jwe.getBoolean(Consts.JWE.IS_INBOX);
    }

    public String getSourceId() {
        return jwe.getString(Consts.JWE.SOURCE_ID);
    }

    public String getStudyInstanceUID() {
        return jwe.getString(Consts.JWE.STUDY_INSTANCE_UID);
    }

    @Override
    public String getSubject() {
        return accessToken.getSubject();
    }

    @Override
    public TokenType getTokenType() { return TokenType.VIEWER_TOKEN; }

    @Override
    public Optional<String> getScope() {
        return Optional.of("read");
    }

    @Override
    public Optional<String> getActingParty() {
        return accessToken.getActingParty();
    }

    @Override
    public Optional<String> getAuthorizedParty() {
        return accessToken.getAuthorizedParty();
    }

    @Override
    public Optional<String> getCapabilityTokenId() {
        return accessToken.getCapabilityTokenId();
    }

    @Override
    public KheopsPrincipal newPrincipal(ServletContext servletContext, User user) {
        return new ViewerPrincipal(servletContext, this);
    }

}
