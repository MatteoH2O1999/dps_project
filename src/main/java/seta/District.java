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

    public static District fromCoordinate(Coordinate c) {
        int x = c.getX();
        int y = c.getY();
        if (x < 5 && y < 5) {
            return new District(1);
        } else if (x < 5 && y > 5) {
            return new District(2);
        } else if (x > 5 && y < 5) {
            return new District(3);
        } else {
            return new District(4);
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
