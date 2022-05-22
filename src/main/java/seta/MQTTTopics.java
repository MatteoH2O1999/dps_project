package seta;

public class MQTTTopics {
    private final static String rideTopic = "seta/smartcity/rides/";
    private final static String ackTopic = "seta/smartcity/rides/ack/";

    public static String getRideTopic(District d) {
        if (d == null) {
            return MQTTTopics.rideTopic + "+";
        }
        return MQTTTopics.rideTopic + "district" + d.getId();
    }

    public static String getAckTopic(District d) {
        if (d == null) {
            return MQTTTopics.ackTopic + "+";
        }
        return MQTTTopics.ackTopic + "district" + d.getId();
    }

    public static boolean isRideTopic(String topic) {
        if (topic.contains(MQTTTopics.rideTopic + "district")) {
            return true;
        }
        return topic.contains(MQTTTopics.getRideTopic(null));
    }

    public static boolean isAckTopic(String topic) {
        if (topic.contains(MQTTTopics.ackTopic + "district")) {
            return true;
        }
        return topic.contains(MQTTTopics.getAckTopic(null));
    }

    public static District districtFromTopic(String topic) {
        String[] split = topic.split("/");
        if (!split[0].equals("seta")) {
            throw new IllegalArgumentException("Expected seta as root topic");
        }
        if (!split[1].equals("smartcity")) {
            throw new IllegalArgumentException("Expected smartcity as first level subtopic");
        }
        if (!split[2].equals("rides")) {
            throw new IllegalArgumentException("Expected rides as second level subtopic");
        }
        if (split[3].contains("district")) {
            String district = split[3].replace("district", "");
            return new District(Integer.parseInt(district));
        } else {
            if (!split[3].equals("ack")) {
                throw new IllegalArgumentException("Expected ack as third level subtopic");
            }
            if (!split[4].contains("district")) {
                throw new IllegalArgumentException("Expected district information");
            }
            String district = split[4].replace("district", "");
            return new District(Integer.parseInt(district));
        }
    }
}
