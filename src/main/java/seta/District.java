package seta;

public class District {
    private int id;

    public District() {
    }

    public District(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Coordinate getRechargeStation() {
        return getRechargeStationFromDistrict(this);
    }

    public static Coordinate getRechargeStationFromDistrict(District d) {
        switch (d.id) {
            case 1:
                return new Coordinate(0, 0);
            case 2:
                return new Coordinate(0, 9);
            case 3:
                return new Coordinate(9, 9);
            case 4:
                return new Coordinate(9, 0);
            default:
                throw new IllegalArgumentException();
        }
    }

    public static District fromCoordinate(Coordinate c) {
        int x = c.getX();
        int y = c.getY();
        if (x < 5 && y < 5) {
            return new District(1);
        } else if (x < 5) {
            return new District(2);
        } else if (y < 5) {
            return new District(4);
        } else {
            return new District(3);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof District)) {
            return false;
        }
        District other = (District) obj;
        return this.id == other.id;
    }
}
