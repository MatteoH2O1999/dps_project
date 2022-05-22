package seta;

public class MQTTTopics {
    private final static String rideTopic = "seta/smartcity/rides/district/";
    private final static String ackTopic = "seta/smartcity/rides/ack/district/";

    public static String getRideTopic(District d) {
        return MQTTTopics.rideTopic + d.getId();
    }

    public static String getAckTopic(District d) {
        return MQTTTopics.ackTopic + d.getId();
    }
}
