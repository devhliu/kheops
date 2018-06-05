package online.kheops.auth_server.resource;

import online.kheops.auth_server.EntityManagerListener;
import online.kheops.auth_server.KheopsPrincipal;
import online.kheops.auth_server.annotation.Secured;
import online.kheops.auth_server.entity.Study;
import online.kheops.auth_server.entity.User;
import org.dcm4che3.data.Attributes;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;

@Path("/users")
public class QIDOResource {

    @GET
    @Secured
    @Path("/{user}/studies")
    @Produces("application/dicom+json")
    public Response getStudies(@PathParam("user") String username,
                               @Context SecurityContext securityContext) {

        // for now don't use any parameters

        // get a list of all the studies
        final long callingUserPk = ((KheopsPrincipal)securityContext.getUserPrincipal()).getDBID();
        // is the user sharing a series, or requesting access to a new series

        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        List<Attributes> attributesList;

        try {
            tx.begin();

            long targetUserPk = User.findPkByUsername(username, em);
            if (callingUserPk != targetUserPk) {
                return Response.status(Response.Status.FORBIDDEN).entity("Access to study denied").build();
            }

            attributesList = new ArrayList<>(Study.findAttributesByUserPK(callingUserPk, em));

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }

        GenericEntity<List<Attributes>> genericAttributesList = new GenericEntity<List<Attributes>>(attributesList) {};
        return Response.ok(genericAttributesList).build();
    }

}
