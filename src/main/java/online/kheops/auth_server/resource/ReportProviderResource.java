package online.kheops.auth_server.resource;

import online.kheops.auth_server.EntityManagerListener;
import online.kheops.auth_server.album.AlbumId;
import online.kheops.auth_server.album.AlbumNotFoundException;
import online.kheops.auth_server.annotation.*;
import online.kheops.auth_server.accesstoken.*;
import online.kheops.auth_server.entity.Album;
import online.kheops.auth_server.entity.ReportProvider;
import online.kheops.auth_server.entity.User;
import online.kheops.auth_server.report_provider.*;
import online.kheops.auth_server.principal.KheopsPrincipal;
import online.kheops.auth_server.token.ReportProviderAccessTokenGenerator;
import online.kheops.auth_server.token.ReportProviderAuthCodeGenerator;
import online.kheops.auth_server.user.UserNotFoundException;
import online.kheops.auth_server.user.AlbumUserPermissions;
import online.kheops.auth_server.util.PairListXTotalCount;
import online.kheops.auth_server.util.KheopsLogBuilder.ActionType;
import online.kheops.auth_server.util.KheopsLogBuilder;
import org.hibernate.validator.constraints.NotEmpty;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.servlet.ServletContext;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.INFO;
import static javax.ws.rs.core.Response.Status.*;
import static online.kheops.auth_server.album.Albums.getAlbum;
import static online.kheops.auth_server.filter.AlbumPermissionSecuredContext.PATH_PARAM;
import static online.kheops.auth_server.report_provider.ReportProviderQueries.getReportProviderWithClientId;
import static online.kheops.auth_server.report_provider.ReportProviders.*;
import static online.kheops.auth_server.study.Studies.canAccessStudy;
import static online.kheops.auth_server.user.AlbumUserPermissions.*;
import static online.kheops.auth_server.user.Users.getOrCreateUser;
import static online.kheops.auth_server.util.Consts.*;
import static online.kheops.auth_server.util.Consts.QUERY_PARAMETER_OFFSET;
import static online.kheops.auth_server.util.HttpHeaders.X_TOTAL_COUNT;
import static online.kheops.auth_server.util.Tools.checkValidUID;


@Path("/")
public class ReportProviderResource {
    private static final Logger LOG = Logger.getLogger(ReportProviderResource.class.getName());

    private static final String HOST_ROOT_PARAMETER = "online.kheops.root.uri";

    @Context
    private UriInfo uriInfo;

    @Context
    private SecurityContext securityContext;

    @Context
    private ServletContext context;

    @POST
    @Secured
    @UserAccessSecured
    @AlbumAccessSecured
    @AlbumPermissionSecured(permission = MANAGE_DICOM_SR, context = PATH_PARAM)
    @Path("albums/{"+ALBUM+":"+ AlbumId.ID_PATTERN+"}/reportproviders")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newReportProvider(@SuppressWarnings("RSReferenceInspection") @PathParam(ALBUM) String albumId,
                            @FormParam("url") @NotNull @NotEmpty final String url,
                            @FormParam("name") @NotNull @NotEmpty final String name) {

        if ( !isValidConfigUrl(url)) {
            return Response.status(BAD_REQUEST).entity("'url' formparam is not valid").build();
        }

        final KheopsPrincipal kheopsPrincipal = ((KheopsPrincipal)securityContext.getUserPrincipal());

        final ReportProviderResponse dicomSrResponse;
        try {
            dicomSrResponse = ReportProviders.newReportProvider(kheopsPrincipal.getUser(), albumId, name, url, kheopsPrincipal.getKheopsLogBuilder());
        } catch (AlbumNotFoundException e) {
            return Response.status(NOT_FOUND).build();
        }

        return Response.status(CREATED).entity(dicomSrResponse).build();
    }

    @POST
    @Path("report")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response newReport(@FormParam("access_token") @NotNull @NotEmpty final String tokenParam,
                              @FormParam("client_id") final String clientId,
                              @FormParam("studyUID") @NotNull @NotEmpty List<String> studyInstanceUIDs) {//Edit UidValidator for work with @FormParam

        for (String uid : studyInstanceUIDs) {
            if (!checkValidUID(uid)) {
                return Response.status(BAD_REQUEST).entity("Bad studyUID").build();
            }
        }

        final AccessToken accessToken;
        try {
            accessToken = AccessTokenVerifier.authenticateAccessToken(context, tokenParam);
        } catch (AccessTokenVerificationException e) {
            LOG.log(Level.WARNING, "Error validating a token", e);
            return Response.status(UNAUTHORIZED).entity("error with the access_token").build();
        } catch (DownloadKeyException e) {
            LOG.log(Level.SEVERE, "Error downloading the public key", e);
            return Response.status(BAD_GATEWAY).entity("Error downloading the public key").build();
        }

        try {
            getOrCreateUser(accessToken.getSubject());
        } catch (UserNotFoundException e) {
            LOG.log(Level.WARNING, "User not found", e);
            return Response.status(UNAUTHORIZED).build();
        }

        if (!  (accessToken.getTokenType() == AccessToken.TokenType.KEYCLOAK_TOKEN ||
                accessToken.getTokenType() == AccessToken.TokenType.USER_CAPABILITY_TOKEN) ) {
            return Response.status(FORBIDDEN).build();
        }

        final User callingUser;
        try {
            callingUser = getOrCreateUser(accessToken.getSubject());
        } catch (UserNotFoundException e) {
            LOG.log(Level.WARNING, "User not found", e);
            return Response.status(UNAUTHORIZED).entity("User not found").build();
        }

        final KheopsPrincipal principal = accessToken.newPrincipal(context, callingUser);

        final EntityManager em = EntityManagerListener.createEntityManager();
        final EntityTransaction tx = em.getTransaction();

        final ReportProvider reportProvider;
        final String albumId;
        try {
            tx.begin();
            reportProvider = getReportProviderWithClientId(clientId, em);
            albumId = reportProvider.getAlbum().getId();
        } catch (NoResultException e) {
            LOG.log(Level.WARNING, "Report provider with clientId: " + clientId + "not found", e);
            return Response.status(NOT_FOUND).entity("Bad clientId").build();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }

        final Album album;
        try {
            if (!(principal.hasUserAccess() && principal.hasAlbumAccess(albumId))) {
                return Response.status(FORBIDDEN).build();
            }

            album = getAlbum(albumId);
            for (String uid : studyInstanceUIDs) {
                if (!canAccessStudy(album, uid)) {
                    return Response.status(NOT_FOUND).entity("StudyUID not found").build();
                }
            }
        } catch (AlbumNotFoundException e) {
            return Response.status(NOT_FOUND).entity(e.getMessage()).build();
        }

        final String responseType;
        try {
            responseType = getResponseType(reportProvider);
        } catch (ReportProviderUriNotValidException e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }

        final String kheopsConfigUrl = getHostRoot() + "/api/.well-known/report-provider-configuration";

        try {
            final String reportProviderUrl;

            if (responseType.equals("code")) {

                ReportProviderAuthCodeGenerator generator = ReportProviderAuthCodeGenerator.createGenerator(context)
                        .withClientId(reportProvider.getClientId())
                        .withStudyInstanceUIDs(studyInstanceUIDs)
                        .withSubject(accessToken.getSubject());

                accessToken.getActingParty().ifPresent(generator::withActingParty);
                accessToken.getCapabilityTokenId().ifPresent(generator::withCapabilityTokenId);

                final String token = generator.generate(600);
                final String confUri = URLEncoder.encode(kheopsConfigUrl, UTF_8.toString());
                final UriBuilder reportProviderUrlBuilder = UriBuilder.fromUri(getRedirectUri(reportProvider))
                        .queryParam("code", token)
                        .queryParam("conf_uri", confUri)
                        .queryParam("client_id", reportProvider.getClientId())
                        .queryParam("return_uri", getHostRoot() + "/albums/" + albumId);

                for (String uid : studyInstanceUIDs) {
                    reportProviderUrlBuilder.queryParam("studyUID", URLEncoder.encode(uid, UTF_8.toString()));
                }

                reportProviderUrl = reportProviderUrlBuilder.toString();

            } else if (responseType.equals("token")) {
                final boolean userHasWriteAccess = principal.hasAlbumPermission(ADD_SERIES, albumId);

                ReportProviderAccessTokenGenerator generator = ReportProviderAccessTokenGenerator.createGenerator(context)
                        .withClientId(clientId)
                        .withScope(userHasWriteAccess ? "read write" : "read")
                        .withStudyInstanceUIDs(studyInstanceUIDs)
                        .withSubject(accessToken.getSubject());

                accessToken.getActingParty().ifPresent(generator::withActingParty);
                accessToken.getCapabilityTokenId().ifPresent(generator::withCapabilityTokenId);

                final String token = generator.generate(3600);

                final String confUri = URLEncoder.encode(kheopsConfigUrl, UTF_8.toString());
                final String returnUri = URLEncoder.encode(getHostRoot() + "/albums/" + albumId, UTF_8.toString());

                reportProviderUrl = UriBuilder.fromUri(getRedirectUri(reportProvider))
                        .fragment("access_token=" + token +
                                "&token_type=" + "bearer" +
                                "&expires_in=3600" +
                                "&scope=" + (userHasWriteAccess ? "read%20write" : "read") +
                                "&client_id=" + clientId +
                                "&conf_uri=" + confUri +
                                "&return_uri=" + returnUri +
                                studyInstanceUIDs.stream()
                                        .map(uid -> "&studyUID=" + uid)
                                        .collect(Collectors.joining()))
                        .toString();

            } else {
                return Response.status(BAD_REQUEST).entity("bad response type").build();
            }
            KheopsLogBuilder kheopsLogBuilder = principal.getKheopsLogBuilder()
                    .action(ActionType.NEW_REPORT)
                    .album(albumId)
                    .clientID(clientId);
            studyInstanceUIDs.forEach(kheopsLogBuilder::study);
            kheopsLogBuilder.log();

            final String html = " <html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                    "  <head>\n" +
                    "    <title>" + reportProvider.getName() + "</title>\n" +
                    "    <meta http-equiv=\"refresh\" content=\"0;URL='" + reportProviderUrl + "'\" />\n" +
                    "  </head>\n" +
                    "  <body>\n" +
                    "    <p>This page has moved to <a href=\"" + reportProviderUrl + "\">\n" +
                    "      here</a>.</p> \n" +
                    "  </body>\n" +
                    "</html>";

            return Response.status(OK).entity(html).build();
        } catch (ReportProviderUriNotValidException e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        } catch (UnsupportedEncodingException e) {
            return Response.status(FORBIDDEN).entity("ERROR").build();
        }
    }

    @GET
    @Path(".well-known/report-provider-configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response configuration() {
        new KheopsLogBuilder().action(ActionType.REPORT_PROVIDER_CONFIGURATION).log();
        return  Response.status(OK).entity(new ConfigurationResponse(getHostRoot())).build();
    }

    @GET
    @Secured
    @UserAccessSecured
    @AlbumAccessSecured
    @AlbumPermissionSecured(permission = GET_DICOM_SR, context = PATH_PARAM)
    @Path("albums/{"+ALBUM+":"+ AlbumId.ID_PATTERN+"}/reportproviders")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllReportProviders(@SuppressWarnings("RSReferenceInspection") @PathParam(ALBUM) String albumId,
                                          @QueryParam(QUERY_PARAMETER_LIMIT) @Min(0) @DefaultValue(""+Integer.MAX_VALUE) Integer limit,
                                          @QueryParam(QUERY_PARAMETER_OFFSET) @Min(0) @DefaultValue("0") Integer offset) {

        final PairListXTotalCount<ReportProviderResponse> pair;

        pair = ReportProviders.getReportProviders(albumId, limit, offset, ((KheopsPrincipal)securityContext.getUserPrincipal()).getKheopsLogBuilder());

        final GenericEntity<List<ReportProviderResponse>> genericReportProvidersResponsesList = new GenericEntity<List<ReportProviderResponse>>(pair.getAttributesList()) {};
        return  Response.status(OK).entity(genericReportProvidersResponsesList).header(X_TOTAL_COUNT, pair.getXTotalCount()).build();
    }

    @GET
    @Secured
    @UserAccessSecured
    @AlbumAccessSecured
    @AlbumPermissionSecured(permission = GET_DICOM_SR, context = PATH_PARAM)
    @Path("albums/{"+ALBUM+":"+ AlbumId.ID_PATTERN+"}/reportproviders/{clientId:"+ ClientId.CLIENT_ID_PATTERN+"}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReportProviders(@SuppressWarnings("RSReferenceInspection") @PathParam(ALBUM) String albumId,
                                       @SuppressWarnings("RSReferenceInspection") @PathParam("clientId") String clientId) {

        final ReportProviderResponse reportProvider;
        try {
            reportProvider = getReportProvider(albumId, clientId, ((KheopsPrincipal)securityContext.getUserPrincipal()).getKheopsLogBuilder());
        } catch (ClientIdNotFoundException e) {
            return Response.status(NOT_FOUND).entity(e.getMessage()).build();
        }

        return  Response.status(OK).entity(reportProvider).build();
    }

    @DELETE
    @Secured
    @UserAccessSecured
    @AlbumAccessSecured
    @AlbumPermissionSecured(permission = MANAGE_DICOM_SR, context = PATH_PARAM)
    @Path("albums/{"+ALBUM+":"+ AlbumId.ID_PATTERN+"}/reportproviders/{clientId:"+ ClientId.CLIENT_ID_PATTERN+"}")
    public Response deleteReportProviders(@SuppressWarnings("RSReferenceInspection") @PathParam(ALBUM) String albumId,
                                          @SuppressWarnings("RSReferenceInspection") @PathParam("clientId") String clientId) {

        final KheopsPrincipal kheopsPrincipal = ((KheopsPrincipal)securityContext.getUserPrincipal());
        final User callingUser = kheopsPrincipal.getUser();
        try {
            deleteReportProvider(callingUser, albumId, clientId, kheopsPrincipal.getKheopsLogBuilder());
        } catch (AlbumNotFoundException | ClientIdNotFoundException e) {
            return Response.status(NOT_FOUND).entity(e.getMessage()).build();
        }

        return  Response.status(NO_CONTENT).build();
    }

    @PATCH
    @Secured
    @UserAccessSecured
    @AlbumAccessSecured
    @AlbumPermissionSecured(permission = MANAGE_DICOM_SR, context = PATH_PARAM)
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("albums/{"+ALBUM+":"+ AlbumId.ID_PATTERN+"}/reportproviders/{clientId:"+ ClientId.CLIENT_ID_PATTERN+"}")
    public Response editReportProviders(@SuppressWarnings("RSReferenceInspection") @PathParam(ALBUM) String albumId,
                                        @SuppressWarnings("RSReferenceInspection") @PathParam("clientId") String clientId,
                                        @FormParam("url") final String url,
                                        @FormParam("name") final String name) {


        if(!(url == null || url.isEmpty() )) {
            if(!isValidConfigUrl(url)) {
                return Response.status(BAD_REQUEST).entity("url not valid").build();
            }
        }

        final KheopsPrincipal kheopsPrincipal = ((KheopsPrincipal)securityContext.getUserPrincipal());
        final User callingUser = kheopsPrincipal.getUser();

        final ReportProviderResponse reportProvider;
        try {
            reportProvider = editReportProvider(callingUser, albumId, clientId, url, name, kheopsPrincipal.getKheopsLogBuilder());
        } catch (AlbumNotFoundException | ClientIdNotFoundException e) {
            return Response.status(NOT_FOUND).entity(e.getMessage()).build();
        }

        return  Response.status(OK).entity(reportProvider).build();
    }

    @POST
    @Secured
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("reportproviders/metadata")
    public Response testUri(@FormParam("url") final String url) {

        if (url == null || url.isEmpty()) {
            return Response.status(BAD_REQUEST).entity("Missing formParam 'url'").build();
        }

        ReportProviderClientMetadata clientMetadataResponse = new ReportProviderClientMetadata();

        try {
            clientMetadataResponse = getClientMetadata(url);
            clientMetadataResponse.setValid(true);
        } catch (ReportProviderUriNotValidException e) {
            LOG.log(INFO, "error validating the configuration url", e);
            clientMetadataResponse.setValid(false);
            clientMetadataResponse.setErrorDescription(e.getMessage());
        }

        ((KheopsPrincipal)securityContext.getUserPrincipal()).getKheopsLogBuilder()
                .action(ActionType.REPORT_PROVIDER_METADATA)
                .log();
        return  Response.status(OK).entity(clientMetadataResponse).build();
    }

    private String getHostRoot() {
        return context.getInitParameter(HOST_ROOT_PARAMETER);
    }
}
