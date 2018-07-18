package online.kheops.zipper;


import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public final class Instance {
    private final String studyInstanceUID;
    private final String seriesInstanceUID;
    private final String sopInstanceUID;

    private Instance(String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID) {
        this.studyInstanceUID = Objects.requireNonNull(studyInstanceUID, "studyInstanceUID");
        this.seriesInstanceUID = Objects.requireNonNull(seriesInstanceUID, "seriesInstanceUID");
        this.sopInstanceUID = Objects.requireNonNull(sopInstanceUID, "sopInstanceUID");
    }

    public static Instance newInstance(String studyInstanceUID, String seriesInstanceUID, String SOPInstanceUID) {
        return new Instance(studyInstanceUID, seriesInstanceUID, SOPInstanceUID);
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    @Override
    public String toString() {
        return "studyInstanceUID:" + studyInstanceUID + " seriesInstanceUID:" + seriesInstanceUID + " sopInstanceUID:" + sopInstanceUID;
    }
}
