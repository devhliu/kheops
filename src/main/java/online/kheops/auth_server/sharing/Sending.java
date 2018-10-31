package online.kheops.auth_server.sharing;

import online.kheops.auth_server.EntityManagerListener;
import online.kheops.auth_server.album.AlbumNotFoundException;
import online.kheops.auth_server.entity.*;
import online.kheops.auth_server.event.Events;
import online.kheops.auth_server.series.SeriesForbiddenException;
import online.kheops.auth_server.series.SeriesNotFoundException;
import online.kheops.auth_server.study.StudyNotFoundException;
import online.kheops.auth_server.user.UserNotFoundException;

import javax.persistence.*;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static online.kheops.auth_server.album.Albums.getAlbum;
import static online.kheops.auth_server.series.SeriesQueries.*;
import static online.kheops.auth_server.study.Studies.getStudy;
import static online.kheops.auth_server.user.Users.getUser;

public class Sending {

    private static final Logger LOG = Logger.getLogger(Sending.class.getName());

    private Sending() {
        throw new IllegalStateException("Utility class");
    }

    public static void deleteStudyFromInbox(long callingUserPk, String studyInstanceUID)
            throws UserNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            User callingUser = getUser(callingUserPk, em);

            final List<Series> seriesList = findSeriesListByStudyUIDFromInbox(callingUser, studyInstanceUID, em);

            if (seriesList.isEmpty()) {
                throw new SeriesNotFoundException("No access to any series with the given studyInstanceUID");
            }

            for (Series series: seriesList) {
                callingUser.getInbox().removeSeries(series, em);
            }

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void deleteSeriesFromInbox(long callingUserPk, String studyInstanceUID, String seriesInstanceUID)
            throws UserNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            User callingUser = getUser(callingUserPk, em);

            Series series;
            try {
                series = findSeriesByStudyUIDandSeriesUIDFromInbox(callingUser, studyInstanceUID, seriesInstanceUID, em);
            } catch (NoResultException e) {
                throw new SeriesNotFoundException("User does not have access to any series with a study with the given studyInstanceUID");
            }

            callingUser.getInbox().removeSeries(series, em);

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void deleteStudyFromAlbum(long callingUserPk, long albumPk, String studyInstanceUID)
            throws UserNotFoundException, AlbumNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(callingUserPk, em);
            final Album callingAlbum = getAlbum(albumPk, em);

            final List<Series> availableSeries = findSeriesListByStudyUIDFromAlbum(callingUser, callingAlbum, studyInstanceUID, em);

            if (availableSeries.isEmpty()) {
                throw new SeriesNotFoundException("No study with the given StudyInstanceUID in the album");
            }

            for (Series series: availableSeries) {
                callingAlbum.removeSeries(series, em);
            }

            final Study study = availableSeries.get(0).getStudy();
            final Mutation mutation = Events.albumPostStudyMutation(callingUser, callingAlbum, Events.MutationType.REMOVE_STUDY, study);

            em.persist(mutation);

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void deleteSeriesFromAlbum(long callingUserPk, long albumPk, String studyInstanceUID, String seriesInstanceUID)
            throws UserNotFoundException, AlbumNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(callingUserPk, em);
            final Album callingAlbum = getAlbum(albumPk, em);

            Series availableSeries;
            try {
                availableSeries = findSeriesByStudyUIDandSeriesUIDFromAlbum(callingUser, callingAlbum, studyInstanceUID, seriesInstanceUID, em);
            } catch (NoResultException e) {
                throw new SeriesNotFoundException("No study with the given StudyInstanceUID in the album");
            }

            callingAlbum.removeSeries(availableSeries, em);
            final Mutation mutation = Events.albumPostSeriesMutation(callingUser, callingAlbum, Events.MutationType.REMOVE_SERIES, availableSeries);

            em.persist(mutation);

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void putSeriesInAlbum(long callingUserPk, long albumPk, String studyInstanceUID, String seriesInstanceUID)
            throws UserNotFoundException, AlbumNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(callingUserPk, em);
            final Album targetAlbum = getAlbum(albumPk, em);

            Series availableSeries;
            try {
                availableSeries = findSeriesByStudyUIDandSeriesUID(studyInstanceUID, seriesInstanceUID, em);

                //Todo series already exist ?? if upload with capability token => verify if orphelin => if not => return forbidden
            } catch (NoResultException e2) {
                // from here the series does not exists
                // find if the study already exists
                Study study;
                try {
                    study = getStudy(studyInstanceUID, em);
                } catch (StudyNotFoundException ignored) {
                    // the study doesn't exist, we need to create it
                    study = new Study();
                    study.setStudyInstanceUID(studyInstanceUID);
                    em.persist(study);
                }

                availableSeries = new Series(seriesInstanceUID);
                study.getSeries().add(availableSeries);
                availableSeries.setStudy(study);
                em.persist(availableSeries);
            }

            if (targetAlbum.containsSeries(availableSeries, em)) {
                return;
            }

            final AlbumSeries albumSeries = new AlbumSeries(targetAlbum, availableSeries, false );
            availableSeries.addAlbumSeries(albumSeries);
            targetAlbum.addSeries(albumSeries);
            em.persist(albumSeries);
            final Mutation mutation = Events.albumPostSeriesMutation(callingUser, targetAlbum, Events.MutationType.IMPORT_SERIES, availableSeries);
            em.persist(mutation);
            //todo if the series is upload with a token...
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void putStudyInAlbum(long callingUserPk, long albumPk, String studyInstanceUID, Long fromAlbumPk, Boolean fromInbox)
            throws UserNotFoundException, AlbumNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(callingUserPk, em);
            final Album targetAlbum = getAlbum(albumPk, em);

            final List<Series> availableSeries = getSeriesList(callingUser, studyInstanceUID, fromAlbumPk, fromInbox, em);

            Boolean allSeriesAlreadyExist = true;
            for (Series series: availableSeries) {
                if (!targetAlbum.containsSeries(series, em)) {
                    final AlbumSeries albumSeries = new AlbumSeries(targetAlbum, series, false );
                    series.addAlbumSeries(albumSeries);
                    targetAlbum.addSeries(albumSeries);
                    em.persist(albumSeries);
                    allSeriesAlreadyExist = false;
                }
            }

            if (allSeriesAlreadyExist) {
                return;
            }
                final Study study = availableSeries.get(0).getStudy();
                final Mutation mutation = Events.albumPostStudyMutation(callingUser, targetAlbum, Events.MutationType.IMPORT_STUDY, study);

                em.persist(mutation);

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void shareStudyWithUser(long callingUserPk, String targetUsername, String studyInstanceUID, Long fromAlbumPk, Boolean fromInbox)
            throws UserNotFoundException, AlbumNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(callingUserPk, em);
            final User targetUser = getUser(targetUsername, em);

            if (callingUserPk == targetUser.getPk()) {
                //return Response.status(Response.Status.BAD_REQUEST).entity("Can't send a study to yourself").build();
                return;
            }

            final List<Series> availableSeries = getSeriesList(callingUser, studyInstanceUID, fromAlbumPk, fromInbox, em);

            final Album inbox = targetUser.getInbox();
            for (Series series : availableSeries) {
                if (!inbox.containsSeries(series, em)) {
                    final AlbumSeries albumSeries = new AlbumSeries(inbox, series, false );
                    series.addAlbumSeries(albumSeries);
                    inbox.addSeries(albumSeries);
                    em.persist(albumSeries);
                }
            }

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void shareSeriesWithUser(long callingUserPk, String targetUsername, String studyInstanceUID, String seriesInstanceUID)
            throws UserNotFoundException, SeriesNotFoundException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User targetUser = getUser(targetUsername, em);

            if (targetUser.getPk() == callingUserPk) { // the user is requesting access to a new series
                //return Response.status(Response.Status.FORBIDDEN).entity("Use studies/{StudyInstanceUID}/series/{SeriesInstanceUID} for request access to a new series").build();
                return;
            }

            final Series series;
            try {
                series = findSeriesByStudyUIDandSeriesUID(studyInstanceUID, seriesInstanceUID, em);
            } catch (NoResultException exception) {
                throw new SeriesNotFoundException("Unknown series");
            }

            final Album inbox = targetUser.getInbox();
            if(inbox.containsSeries(series, em)) {
                //return Response.status(Response.Status.OK).build();
                return;
            }

            final AlbumSeries albumSeries = new AlbumSeries(inbox, series, false );
            series.addAlbumSeries(albumSeries);
            inbox.addSeries(albumSeries);
            em.persist(albumSeries);

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static void appropriateSeries(long callingUserPk, String studyInstanceUID, String seriesInstanceUID)
            throws UserNotFoundException, SeriesForbiddenException {
        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(callingUserPk, em);

            try {
                final Series storedSeries = findSeriesByStudyUIDandSeriesUID(studyInstanceUID, seriesInstanceUID, em);

                if(!isOrphan(storedSeries, em)) {
                    try {
                        findSeriesBySeriesAndUserInbox(callingUser, storedSeries, em);
                        return;
                    } catch (NoResultException e) {
                        throw new SeriesForbiddenException("TODO the series already exist");//TODO
                    }

                }

                // here the series exists but she is orphan or the calling can send the series from an album
                final Album inbox = callingUser.getInbox();
                final AlbumSeries albumSeries = new AlbumSeries(inbox, storedSeries, false );
                storedSeries.addAlbumSeries(albumSeries);
                inbox.addSeries(albumSeries);
                em.persist(albumSeries);
                tx.commit();
                LOG.info("Claim accepted because the series is inside an album where the calling user (" + callingUser.getGoogleId() + ") is member, StudyInstanceUID:" + studyInstanceUID + ", SeriesInstanceUID:" + seriesInstanceUID);
                return;

            } catch (NoResultException ignored) {/*empty*/}

            // from here the series does not exists
            // find if the study already exists
            Study study;
            try {
                study = getStudy(studyInstanceUID, em);
            } catch (StudyNotFoundException ignored) {
                // the study doesn't exist, we need to create it
                study = new Study();
                study.setStudyInstanceUID(studyInstanceUID);
                em.persist(study);
            }

            final Series series = new Series(seriesInstanceUID);
            study.getSeries().add(series);
            Album inbox = callingUser.getInbox();
            final AlbumSeries albumSeries = new AlbumSeries(inbox, series, false );
            series.addAlbumSeries(albumSeries);
            inbox.addSeries(albumSeries);

            em.persist(series);
            em.persist(albumSeries);
            LOG.info("finished claiming, StudyInstanceUID:" + studyInstanceUID + ", SeriesInstanceUID:" + seriesInstanceUID + " to " + callingUser.getGoogleId());

            tx.commit();
            LOG.info("sending, StudyInstanceUID:" + studyInstanceUID + ", SeriesInstanceUID:" + seriesInstanceUID + " to " + callingUser.getGoogleId());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    public static Set<String> availableSeriesUIDs(long userPk, String studyInstanceUID, Long fromAlbumPk, Boolean fromInbox)
            throws UserNotFoundException, AlbumNotFoundException , StudyNotFoundException {
        Set<String> availableSeriesUIDs;

        EntityManager em = EntityManagerListener.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();

            final User callingUser = getUser(userPk, em);

            if (fromAlbumPk != null) {
                final Album album = getAlbum(fromAlbumPk, em);
                availableSeriesUIDs = findAllSeriesInstanceUIDbySeriesIUIDfromAlbum(callingUser, album, studyInstanceUID, em);
            } else if (fromInbox) {
                availableSeriesUIDs = findAllSeriesInstanceUIDbySeriesIUIDfromInbox(callingUser, studyInstanceUID, em);
            } else {
                availableSeriesUIDs = findAllSeriesInstanceUIDbySeriesIUIDfromAlbumandInbox(callingUser, studyInstanceUID, em);
            }

            tx.commit();
        } catch (NoResultException e) {
            throw new StudyNotFoundException("StudyInstanceUID : "+studyInstanceUID+" not found");
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }

        return availableSeriesUIDs;
    }

    public static  List<Series> getSeriesList(User callingUser, String studyInstanceUID, Long fromAlbumPk, Boolean fromInbox, EntityManager em)
            throws AlbumNotFoundException , SeriesNotFoundException{
        List<Series> availableSeries;

        if (fromAlbumPk != null) {
            final Album callingAlbum = getAlbum(fromAlbumPk, em);

            availableSeries = findSeriesListByStudyUIDFromAlbum(callingUser, callingAlbum, studyInstanceUID, em);
        } else if (fromInbox) {
            availableSeries = findSeriesListByStudyUIDFromInbox(callingUser, studyInstanceUID, em);
        } else {
            availableSeries = findSeriesListByStudyUIDFromAlbumAndInbox(callingUser, studyInstanceUID, em);
        }

        if (availableSeries.isEmpty()) {
            throw new SeriesNotFoundException("No study with the given StudyInstanceUID");
        }
        return availableSeries;
    }
}
