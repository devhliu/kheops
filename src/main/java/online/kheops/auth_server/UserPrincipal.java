package online.kheops.auth_server;

import online.kheops.auth_server.album.AlbumNotFoundException;
import online.kheops.auth_server.capability.ScopeType;
import online.kheops.auth_server.entity.*;
import online.kheops.auth_server.series.SeriesNotFoundException;
import online.kheops.auth_server.study.StudyNotFoundException;
import online.kheops.auth_server.user.UsersPermission;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.ws.rs.NotFoundException;

import static online.kheops.auth_server.album.Albums.getAlbum;
import static online.kheops.auth_server.album.Albums.getAlbumUser;
import static online.kheops.auth_server.series.Series.canAccessSeries;

import static online.kheops.auth_server.series.Series.getSeries;
import static online.kheops.auth_server.series.SeriesQueries.*;
import static online.kheops.auth_server.study.Studies.canAccessStudy;
import static online.kheops.auth_server.study.Studies.getStudy;

public class UserPrincipal implements KheopsPrincipalInterface {

    private EntityManager em;
    private EntityTransaction tx;
    private final User user;

    //old version
    Long dbid;
    public UserPrincipal(User user) {
        this.dbid=user.getPk();
        this.user = user;
    }
    @Override
    public long getDBID() {
        return dbid;
    }
    @Override
    public String getName() {
        return Long.toString(dbid);
    }
    //end old version

    @Override
    public boolean hasSeriesReadAccess(String studyInstanceUID, String seriesInstanceUID) throws SeriesNotFoundException{
        this.em = EntityManagerListener.createEntityManager();
        this.tx = em.getTransaction();
        try {
            tx.begin();
            if (canAccessSeries(user, studyInstanceUID, seriesInstanceUID, em)) {
                return true;
            } else {
                throw new SeriesNotFoundException("seriesInstanceUID : " + seriesInstanceUID + " not found");
            }
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    @Override
    public boolean hasStudyReadAccess(String studyInstanceUID) throws StudyNotFoundException {
        this.em = EntityManagerListener.createEntityManager();
        this.tx = em.getTransaction();
        try {
            tx.begin();
            final Study study = getStudy(studyInstanceUID, em);
            if (canAccessStudy(user, study, em)) {
                return true;
            } else {
                throw new StudyNotFoundException("studyInstanceUID : " + studyInstanceUID + " not found");
            }
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    @Override
    public boolean hasUserAccess() { return true; }

    @Override
    public boolean hasSeriesWriteAccess(String studyInstanceUID, String seriesInstanceUID) throws SeriesNotFoundException{
        this.em = EntityManagerListener.createEntityManager();
        this.tx = em.getTransaction();
        try {
            tx.begin();

            //find if the series exist
            final Series series;
            try {
                series = getSeries(studyInstanceUID, seriesInstanceUID, em);
            } catch (SeriesNotFoundException e) {
                return true;
            }

            // we need to check here if the series that was found is owned by the user
            try {
                findSeriesBySeriesAndUserInbox(user, series, em);
                return true;
            } catch (NoResultException ignored) {/*empty*/}

            try {
                findSeriesBySeriesAndAlbumWithSendPermission(user, series, em);
                return true;
            } catch (NoResultException ignored) {
                if (isOrphan(series, em)) {
                    return true;
                }
            }
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
        return false;
    }

    @Override
    public boolean hasStudyWriteAccess(String study) { return true; }

    @Override
    public boolean hasAlbumPermission(UsersPermission.UsersPermissionEnum usersPermission, Long albumId) throws AlbumNotFoundException {
        this.em = EntityManagerListener.createEntityManager();
        this.tx = em.getTransaction();
        try {
            tx.begin();

            final User userMerge = em.merge(user);
            final Album album = getAlbum(albumId, em);
            final AlbumUser albumUser = getAlbumUser(album, userMerge, em);

            if(userMerge.getInbox() == album) {
                throw new AlbumNotFoundException("Album id : " + albumId + " not found");
            }
            if (albumUser.isAdmin()) {
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.ADD_SERIES && album.isAddSeries()){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.ADD_USER && album.isAddUser()){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.DELETE_SERIES && album.isDeleteSeries()){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.DOWNLOAD_SERIES && album.isDownloadSeries()){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.SEND_SERIES && album.isSendSeries()){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.WRITE_COMMENT && album.isWriteComments()){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.REMOVE_USER){
                return true;
            }
            if (usersPermission == UsersPermission.UsersPermissionEnum.LIST_USERS){
                return true;
            }
            return usersPermission == UsersPermission.UsersPermissionEnum.EDIT_ALBUM;

        } catch (AlbumNotFoundException e) {
            throw new AlbumNotFoundException("Album id : " + albumId + " not found");
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    @Override
    public boolean hasAlbumAccess(Long albumId) throws AlbumNotFoundException{
        this.em = EntityManagerListener.createEntityManager();
        this.tx = em.getTransaction();
        try {
            tx.begin();
            final User userMerge = em.merge(user);
            final Album album = getAlbum(albumId, em);
            final AlbumUser albumUser = getAlbumUser(album, userMerge, em);//user.memberOf(album)
            if (userMerge.getInbox() == album) {
                throw new AlbumNotFoundException("Album id : " + albumId + " not found");
            } else {
                return true;
            }
        } catch (AlbumNotFoundException e) {
            throw new AlbumNotFoundException("Album id : " + albumId + " not found");
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            em.close();
        }
    }

    @Override
    public User getUser() { return user; }

    @Override
    public ScopeType getScope() { return ScopeType.USER; }
}
