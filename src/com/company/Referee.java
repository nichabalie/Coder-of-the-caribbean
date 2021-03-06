package com.company;

import com.sun.org.apache.regexp.internal.RE;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Ref;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Referee {
    private static final int LEAGUE_LEVEL = 3;

    public static final int MAP_WIDTH = 23;
    public static final int MAP_HEIGHT = 21;
    public static final int COOLDOWN_CANNON = 2;
    public static final int COOLDOWN_MINE = 5;
    public static final int INITIAL_SHIP_HEALTH = 100;
    public static final int MAX_SHIP_HEALTH = 100;
    public static final int MAX_SHIP_SPEED;
    public static final int MIN_SHIPS = 1;
    public static final int MAX_SHIPS;
    public static final int MIN_MINES;
    public static final int MAX_MINES;
    public static final int MIN_RUM_BARRELS = 10;
    public static final int MAX_RUM_BARRELS = 26;
    public static final int MIN_RUM_BARREL_VALUE = 10;
    public static final int MAX_RUM_BARREL_VALUE = 20;
    public static final int REWARD_RUM_BARREL_VALUE = 30;
    public static final int MINE_VISIBILITY_RANGE = 5;
    public static final int FIRE_DISTANCE_MAX = 10;
    public static final int LOW_DAMAGE = 25;
    public static final int HIGH_DAMAGE = 50;
    public static final int MINE_DAMAGE = 25;
    public static final int NEAR_MINE_DAMAGE = 10;
    public static final boolean CANNONS_ENABLED;
    public static final boolean MINES_ENABLED;

    static {
        switch (LEAGUE_LEVEL) {
            case 0: // 1 ship / no mines / speed 1
                MAX_SHIPS = 1;
                CANNONS_ENABLED = false;
                MINES_ENABLED = false;
                MIN_MINES = 0;
                MAX_MINES = 0;
                MAX_SHIP_SPEED = 1;
                break;
            case 1: // add mines
                MAX_SHIPS = 1;
                CANNONS_ENABLED = true;
                MINES_ENABLED = true;
                MIN_MINES = 5;
                MAX_MINES = 10;
                MAX_SHIP_SPEED = 1;
                break;
            case 2: // 3 ships max
                MAX_SHIPS = 3;
                CANNONS_ENABLED = true;
                MINES_ENABLED = true;
                MIN_MINES = 5;
                MAX_MINES = 10;
                MAX_SHIP_SPEED = 1;
                break;
            default: // increase max speed
                MAX_SHIPS = 3;
                CANNONS_ENABLED = true;
                MINES_ENABLED = true;
                MIN_MINES = 5;
                MAX_MINES = 10;
                MAX_SHIP_SPEED = 2;
                break;
        }
    }

    private static final Pattern PLAYER_INPUT_MOVE_PATTERN = Pattern.compile("MOVE (?<x>[0-9]{1,8})\\s+(?<y>[0-9]{1,8})(?:\\s+(?<message>.+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_SLOWER_PATTERN = Pattern.compile("SLOWER(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_FASTER_PATTERN = Pattern.compile("FASTER(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_WAIT_PATTERN = Pattern.compile("WAIT(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_PORT_PATTERN = Pattern.compile("PORT(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_STARBOARD_PATTERN = Pattern.compile("STARBOARD(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_FIRE_PATTERN = Pattern.compile("FIRE (?<x>[0-9]{1,8})\\s+(?<y>[0-9]{1,8})(?:\\s+(?<message>.+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_MINE_PATTERN = Pattern.compile("MINE(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    @SafeVarargs
    static final <T> String join(T... v) {
        return Stream.of(v).map(String::valueOf).collect(Collectors.joining(" "));
    }

    public static class Coord {
        private final static int[][] DIRECTIONS_EVEN = new int[][] { { 1, 0 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 }, { 0, 1 } };
        private final static int[][] DIRECTIONS_ODD = new int[][] { { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 1, 1 } };
        private final int x;
        private final int y;

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public Coord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Coord(Coord other) {
            this.x = other.x;
            this.y = other.y;
        }

        public Coord clone() {
            Coord coord = new Coord(x, y);
            return coord;
        }

        public double angle(Coord targetPosition) {
            double dy = (targetPosition.y - this.y) * Math.sqrt(3) / 2;
            double dx = targetPosition.x - this.x + ((this.y - targetPosition.y) & 1) * 0.5;
            double angle = -Math.atan2(dy, dx) * 3 / Math.PI;
            if (angle < 0) {
                angle += 6;
            } else if (angle >= 6) {
                angle -= 6;
            }
            return angle;
        }

        CubeCoordinate toCubeCoordinate() {
            int xp = x - (y - (y & 1)) / 2;
            int zp = y;
            int yp = -(xp + zp);
            return new CubeCoordinate(xp, yp, zp);
        }

        Coord neighbor(int orientation) {
            int newY, newX;
            if (this.y % 2 == 1) {
                newY = this.y + DIRECTIONS_ODD[orientation][1];
                newX = this.x + DIRECTIONS_ODD[orientation][0];
            } else {
                newY = this.y + DIRECTIONS_EVEN[orientation][1];
                newX = this.x + DIRECTIONS_EVEN[orientation][0];
            }

            return new Coord(newX, newY);
        }

        boolean isInsideMap() {
            return x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT;
        }

        int distanceTo(Coord dst) {
            return this.toCubeCoordinate().distanceTo(dst.toCubeCoordinate());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Coord other = (Coord) obj;
            return y == other.y && x == other.x;
        }

        @Override
        public String toString() {
            return join(x, y);
        }
    }

    public static class CubeCoordinate {
        static int[][] directions = new int[][] { { 1, -1, 0 }, { +1, 0, -1 }, { 0, +1, -1 }, { -1, +1, 0 }, { -1, 0, +1 }, { 0, -1, +1 } };
        int x, y, z;

        public CubeCoordinate(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Coord toOffsetCoordinate() {
            int newX = x + (z - (z & 1)) / 2;
            int newY = z;
            return new Coord(newX, newY);
        }

        CubeCoordinate neighbor(int orientation) {
            int nx = this.x + directions[orientation][0];
            int ny = this.y + directions[orientation][1];
            int nz = this.z + directions[orientation][2];

            return new CubeCoordinate(nx, ny, nz);
        }

        int distanceTo(CubeCoordinate dst) {
            return (Math.abs(x - dst.x) + Math.abs(y - dst.y) + Math.abs(z - dst.z)) / 2;
        }

        @Override
        public String toString() {
            return join(x, y, z);
        }
    }

    private static enum EntityType {
        SHIP, BARREL, MINE, CANNONBALL
    }

    public static abstract class Entity {
        private static int UNIQUE_ENTITY_ID = 0;

        protected int id;
        protected final EntityType type;
        protected Coord position;

        public Entity(EntityType type, int x, int y) {
            this.id = UNIQUE_ENTITY_ID++;
            this.type = type;
            this.position = new Coord(x, y);
        }

        public Entity(EntityType type, int x, int y, int id) {
            this.id = id;
            this.type = type;
            this.position = new Coord(x, y);
        }

        public int getId() {
            return id;
        }

        public Entity getNearestEntity(List<Entity> entities) {

            int minimalDistance = Integer.MAX_VALUE;
            Entity nearestEntity = null;

            for (Entity entity : entities) {
                int distance = this.distanceTo(entity);
                if (distance < minimalDistance) {
                    minimalDistance = distance;
                    nearestEntity = entity;
                }
            }
            return nearestEntity;
        }


        public int distanceTo(Entity entity) {
            return this.position.distanceTo(entity.position);
        }

        public String toViewString() {
            return join(id, position.y, position.x);
        }

        protected String toPlayerString(int arg1, int arg2, int arg3, int arg4) {
            return join(id, type.name(), position.x, position.y, arg1, arg2, arg3, arg4);
        }

        public String toPositionString() {
            return this.position.toString();
        }

    }

    public static class Mine extends Entity {
        public Mine(int x, int y) {
            super(EntityType.MINE, x, y);
        }

        public Mine clone() {
            Mine mine = new Mine(this.position.getX(),this.position.getY());
            return mine;
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(0, 0, 0, 0);
        }

        public List<Damage> explode(List<Ship> ships, boolean force) {
            List<Damage> damage = new ArrayList<>();
            Ship victim = null;

            for (Ship ship : ships) {
                if (position.equals(ship.bow()) || position.equals(ship.stern()) || position.equals(ship.position)) {
                    damage.add(new Damage(this.position, MINE_DAMAGE, true));
                    ship.damage(MINE_DAMAGE);
                    victim = ship;
                }
            }

            if (force || victim != null) {
                if (victim == null) {
                    damage.add(new Damage(this.position, MINE_DAMAGE, true));
                }

                for (Ship ship : ships) {
                    if (ship != victim) {
                        Coord impactPosition = null;
                        if (ship.stern().distanceTo(position) <= 1) {
                            impactPosition = ship.stern();
                        }
                        if (ship.bow().distanceTo(position) <= 1) {
                            impactPosition = ship.bow();
                        }
                        if (ship.position.distanceTo(position) <= 1) {
                            impactPosition = ship.position;
                        }

                        if (impactPosition != null) {
                            ship.damage(NEAR_MINE_DAMAGE);
                            damage.add(new Damage(impactPosition, NEAR_MINE_DAMAGE, true));
                        }
                    }
                }
            }

            return damage;
        }
    }

    public static class Cannonball extends Entity {
        final int ownerEntityId;
        final int srcX;
        final int srcY;
        final int initialRemainingTurns;
        int remainingTurns;

        public Cannonball(int row, int col, int ownerEntityId, int srcX, int srcY, int remainingTurns) {
            super(EntityType.CANNONBALL, row, col);
            this.ownerEntityId = ownerEntityId;
            this.srcX = srcX;
            this.srcY = srcY;
            this.initialRemainingTurns = this.remainingTurns = remainingTurns;
        }

        public Cannonball clone() {
            Cannonball cannonball = new Cannonball(position.getX(), position.getY(), ownerEntityId, srcX, srcY, remainingTurns);
            return cannonball;
        }

        public String toViewString() {
            return join(id, position.y, position.x, srcY, srcX, initialRemainingTurns, remainingTurns, ownerEntityId);
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(ownerEntityId, remainingTurns, 0, 0);
        }
    }

    public static class RumBarrel extends Entity {
        private int health;

        public RumBarrel(int x, int y, int health) {
            super(EntityType.BARREL, x, y);
            this.health = health;
        }

        public RumBarrel clone() {
            RumBarrel rumBarrel = new RumBarrel(this.position.getX(), this.position.getY(), health);
            return  rumBarrel;
        }

        public String toViewString() {
            return join(id, position.y, position.x, health);
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(health, 0, 0, 0);
        }
    }

    public static class Damage {
        private final Coord position;
        private final int health;
        private final boolean hit;

        public Damage(Coord position, int health, boolean hit) {
            this.position = position;
            this.health = health;
            this.hit = hit;
        }

        public Damage clone() {
            Damage damage = new Damage(this.position.clone(),  this.health, this.hit);
            return damage;
        }

        public String toViewString() {
            return join(position.y, position.x, health, (hit ? 1 : 0));
        }
    }

    public static enum Action {
        FASTER, SLOWER, PORT, STARBOARD, FIRE, MINE
    }

    public static class Ship extends Entity {


        int orientation;

        int speed;
        int health;
        int owner;
        String message;
        Action action;
        int mineCooldown;
        int cannonCooldown;
        Coord target;
        public int newOrientation;
        public Coord newPosition;
        public Coord newBowCoordinate;
        public Coord newSternCoordinate;

        public Ship(int x, int y, int orientation, int owner) {
            super(EntityType.SHIP, x, y);
            this.orientation = orientation;
            this.speed = 0;
            this.health = INITIAL_SHIP_HEALTH;
            this.owner = owner;
        }

        public Ship(int id, int x, int y, int orientation, int speed, int health, int owner) {
            this(x,y,orientation,owner);
            this.id = id;
            this.health = health;
            this.speed = speed;
        }


        public Ship clone() {
            Ship ship = new Ship(id, position.getX(),  position.getY(),  orientation, speed, health, owner);
            return ship;
        }

        public int getOrientation() {
            return orientation;
        }

        public void setOrientation(int orientation) {
            this.orientation = orientation;
        }

        public int getSpeed() {
            return speed;
        }

        public void setSpeed(int speed) {
            this.speed = speed;
        }

        public int getHealth() {
            return health;
        }

        public void setHealth(int health) {
            this.health = health;
        }

        public String toViewString() {
            return join(id, position.y, position.x, orientation, health, speed, (action != null ? action : "WAIT"), bow().y, bow().x, stern().y,
                    stern().x, " ;" + (message != null ? message : ""));
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(orientation, speed, health, owner == playerIdx ? 1 : 0);
        }

        public void setMessage(String message) {
            if (message != null && message.length() > 50) {
                message = message.substring(0, 50) + "...";
            }
            this.message = message;
        }

        public void moveTo(int x, int y) {
            Coord currentPosition = this.position;
            Coord targetPosition = new Coord(x, y);

            if (currentPosition.equals(targetPosition)) {
                this.action = Action.SLOWER;
                return;
            }

            double targetAngle, angleStraight, anglePort, angleStarboard, centerAngle, anglePortCenter, angleStarboardCenter;

            switch (speed) {
                case 2:
                    this.action = Action.SLOWER;
                    break;
                case 1:
                    // Suppose we've moved first
                    currentPosition = currentPosition.neighbor(orientation);
                    if (!currentPosition.isInsideMap()) {
                        this.action = Action.SLOWER;
                        break;
                    }

                    // Target reached at next turn
                    if (currentPosition.equals(targetPosition)) {
                        this.action = null;
                        break;
                    }

                    // For each neighbor cell, find the closest to target
                    targetAngle = currentPosition.angle(targetPosition);
                    angleStraight = Math.min(Math.abs(orientation - targetAngle), 6 - Math.abs(orientation - targetAngle));
                    anglePort = Math.min(Math.abs((orientation + 1) - targetAngle), Math.abs((orientation - 5) - targetAngle));
                    angleStarboard = Math.min(Math.abs((orientation + 5) - targetAngle), Math.abs((orientation - 1) - targetAngle));

                    centerAngle = currentPosition.angle(new Coord(MAP_WIDTH / 2, MAP_HEIGHT / 2));
                    anglePortCenter = Math.min(Math.abs((orientation + 1) - centerAngle), Math.abs((orientation - 5) - centerAngle));
                    angleStarboardCenter = Math.min(Math.abs((orientation + 5) - centerAngle), Math.abs((orientation - 1) - centerAngle));

                    // Next to target with bad angle, slow down then rotate (avoid to turn around the target!)
                    if (currentPosition.distanceTo(targetPosition) == 1 && angleStraight > 1.5) {
                        this.action = Action.SLOWER;
                        break;
                    }

                    Integer distanceMin = null;

                    // Test forward
                    Coord nextPosition = currentPosition.neighbor(orientation);
                    if (nextPosition.isInsideMap()) {
                        distanceMin = nextPosition.distanceTo(targetPosition);
                        this.action = null;
                    }

                    // Test port
                    nextPosition = currentPosition.neighbor((orientation + 1) % 6);
                    if (nextPosition.isInsideMap()) {
                        int distance = nextPosition.distanceTo(targetPosition);
                        if (distanceMin == null || distance < distanceMin || distance == distanceMin && anglePort < angleStraight - 0.5) {
                            distanceMin = distance;
                            this.action = Action.PORT;
                        }
                    }

                    // Test starboard
                    nextPosition = currentPosition.neighbor((orientation + 5) % 6);
                    if (nextPosition.isInsideMap()) {
                        int distance = nextPosition.distanceTo(targetPosition);
                        if (distanceMin == null || distance < distanceMin
                                || (distance == distanceMin && angleStarboard < anglePort - 0.5 && this.action == Action.PORT)
                                || (distance == distanceMin && angleStarboard < angleStraight - 0.5 && this.action == null)
                                || (distance == distanceMin && this.action == Action.PORT && angleStarboard == anglePort
                                && angleStarboardCenter < anglePortCenter)
                                || (distance == distanceMin && this.action == Action.PORT && angleStarboard == anglePort
                                && angleStarboardCenter == anglePortCenter && (orientation == 1 || orientation == 4))) {
                            distanceMin = distance;
                            this.action = Action.STARBOARD;
                        }
                    }
                    break;
                case 0:
                    // Rotate ship towards target
                    targetAngle = currentPosition.angle(targetPosition);
                    angleStraight = Math.min(Math.abs(orientation - targetAngle), 6 - Math.abs(orientation - targetAngle));
                    anglePort = Math.min(Math.abs((orientation + 1) - targetAngle), Math.abs((orientation - 5) - targetAngle));
                    angleStarboard = Math.min(Math.abs((orientation + 5) - targetAngle), Math.abs((orientation - 1) - targetAngle));

                    centerAngle = currentPosition.angle(new Coord(MAP_WIDTH / 2, MAP_HEIGHT / 2));
                    anglePortCenter = Math.min(Math.abs((orientation + 1) - centerAngle), Math.abs((orientation - 5) - centerAngle));
                    angleStarboardCenter = Math.min(Math.abs((orientation + 5) - centerAngle), Math.abs((orientation - 1) - centerAngle));

                    Coord forwardPosition = currentPosition.neighbor(orientation);

                    this.action = null;

                    if (anglePort <= angleStarboard) {
                        this.action = Action.PORT;
                    }

                    if (angleStarboard < anglePort || angleStarboard == anglePort && angleStarboardCenter < anglePortCenter
                            || angleStarboard == anglePort && angleStarboardCenter == anglePortCenter && (orientation == 1 || orientation == 4)) {
                        this.action = Action.STARBOARD;
                    }

                    if (forwardPosition.isInsideMap() && angleStraight <= anglePort && angleStraight <= angleStarboard) {
                        this.action = Action.FASTER;
                    }
                    break;
            }

        }

        public void faster() {
            this.action = Action.FASTER;
        }

        public void slower() {
            this.action = Action.SLOWER;
        }

        public void port() {
            this.action = Action.PORT;
        }

        public void starboard() {
            this.action = Action.STARBOARD;
        }

        public void placeMine() {
            if (MINES_ENABLED) {
                this.action = Action.MINE;
            }
        }

        public Coord stern() {
            return position.neighbor((orientation + 3) % 6);
        }

        public Coord bow() {
            return position.neighbor(orientation);
        }

        public Coord newStern() {
            return position.neighbor((newOrientation + 3) % 6);
        }

        public Coord newBow() {
            return position.neighbor(newOrientation);
        }

        public boolean at(Coord coord) {
            Coord stern = stern();
            Coord bow = bow();
            return stern != null && stern.equals(coord) || bow != null && bow.equals(coord) || position.equals(coord);
        }

        public boolean newBowIntersect(Ship other) {
            return newBowCoordinate != null && (newBowCoordinate.equals(other.newBowCoordinate) || newBowCoordinate.equals(other.newPosition)
                    || newBowCoordinate.equals(other.newSternCoordinate));
        }

        public boolean newBowIntersect(List<Ship> ships) {
            for (Ship other : ships) {
                if (this != other && newBowIntersect(other)) {
                    return true;
                }
            }
            return false;
        }

        public boolean newPositionsIntersect(Ship other) {
            boolean sternCollision = newSternCoordinate != null && (newSternCoordinate.equals(other.newBowCoordinate)
                    || newSternCoordinate.equals(other.newPosition) || newSternCoordinate.equals(other.newSternCoordinate));
            boolean centerCollision = newPosition != null && (newPosition.equals(other.newBowCoordinate) || newPosition.equals(other.newPosition)
                    || newPosition.equals(other.newSternCoordinate));
            return newBowIntersect(other) || sternCollision || centerCollision;
        }

        public boolean newPositionsIntersect(List<Ship> ships) {
            for (Ship other : ships) {
                if (this != other && newPositionsIntersect(other)) {
                    return true;
                }
            }
            return false;
        }

        public void damage(int health) {

            if(this.owner == 1) {
                myHealtWin -= health;
            } else {
                ennemyHealtWin -= health;
            }

            this.health -= health;
            if (this.health <= 0) {
                this.health = 0;
            }
        }

        public void heal(int health) {
            this.health += health;

            if(this.owner == 1) {
                myHealtWin += health;
            } else {
                ennemyHealtWin += health;
            }

            if (this.health > MAX_SHIP_HEALTH) {
                this.health = MAX_SHIP_HEALTH;
            }
        }

        public void fire(int x, int y) {
            if (CANNONS_ENABLED) {
                Coord target = new Coord(x, y);
                this.target = target;
                this.action = Action.FIRE;
            }
        }
    }

    protected static class Player {
        private int id;
        private List<Ship> ships;
        private List<Ship> shipsAlive;

        public Player(int id) {
            this.id = id;
            this.ships = new ArrayList<>();
            this.shipsAlive = new ArrayList<>();
        }

        @Override
        public Player clone() {
            Player player = new Player(this.id);
            for(Ship ship : ships) {
                Ship shipClone = ship.clone();
                player.ships.add(shipClone);
                if(shipsAlive.contains(ship))
                    player.shipsAlive.add(shipClone);
            }

            return player;
        }

        public void addShip(Ship ship) {
            ships.add(ship);
            shipsAlive.add(ship);
        }

        public void clearShip() {
            shipsAlive.clear();
            ships.clear();
        }

        public List<Ship> getShips() {
            return ships;
        }

        public List<Ship> getShipsAlive() {
            return shipsAlive;
        }

        public void setDead() {
            for (Ship ship : ships) {
                ship.health = 0;
            }
        }

        public int getScore() {
            int score = 0;
            for (Ship ship : ships) {
                score += ship.health;
            }
            return score;
        }

        public List<String> toViewString() {
            List<String> data = new ArrayList<>();

            data.add(String.valueOf(this.id));
            for (Ship ship : ships) {
                data.add(ship.toViewString());
            }

            return data;
        }
    }

    private long seed;
    private List<Cannonball> cannonballs;
    private List<Mine> mines;
    private List<RumBarrel> barrels;
    private List<Player> players;
    private List<Ship> ships;
    private List<Damage> damage;
    private List<Ship> shipLosts;
    private List<Coord> cannonBallExplosions;
    private int shipsPerPlayer;
    private int mineCount;
    private int barrelCount;
    private Random random;

    //FIXME: static variable not necessary (and realy ugly here)
    public static int ennemyHealtWin = 0;
    public static int myHealtWin = 0;


    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
    }

    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err);
    }

    // Copy constructor
    public Referee(Referee ref) {

        ennemyHealtWin = 0;
        myHealtWin = 0;

        this.seed = ref.seed;


        this.mines = new ArrayList<>();
        for(Mine mine : ref.mines) {
            this.mines.add(mine.clone());
        }

        this.barrels = new ArrayList<>();
        for(RumBarrel rumBarrel : ref.barrels) {
            this.barrels.add(rumBarrel.clone());
        }

        this.players = new ArrayList<>();
        for(Player player : ref.players) {
            this.players.add(player.clone());
        }


        this.ships = new ArrayList<>();
        for(Player player : players) {
            ships.addAll(player.ships);
            //System.err.println("Ships size: " + ships.size());
        }

        this.damage = new ArrayList<>();
        for(Damage damage : ref.damage) {
            this.damage.add(damage.clone());
        }

        this.shipLosts = new ArrayList<>();
        for(Ship ship : ref.shipLosts) {
            this.shipLosts.add(ship.clone());
        }

        this.cannonballs = new ArrayList<>();
        for(Cannonball cannonball : ref.cannonballs) {
            this.cannonballs.add(cannonball.clone());
        }

        this.cannonBallExplosions = new ArrayList<>();
        for(Coord cannonballExplosion : ref.cannonBallExplosions) {
            this.cannonBallExplosions.add(cannonballExplosion.clone());
        }

        this.cannonballs = ref.cannonballs;
        this.cannonBallExplosions = ref.cannonBallExplosions;


        this.shipsPerPlayer = ref.shipsPerPlayer;


        this.mineCount = ref.mineCount;
        this.barrelCount = ref.barrelCount;
        this.random = ref.random;
    }

    Comparator<Ship> idComparator = new Comparator<Ship>() {
        public int compare(Ship o1, Ship o2) {
            if (o1.id == o2.id) {
                return 1;
            }
            return 0;
        }
    };

    /**
     * Evaluation of the score between first referee (the current) and the referee after few rounds (the secondRef)
     * @param secondRef
     * @return the score
     */
    public int evaluateScore(Referee secondRef) {

        int score = 0;

        int myShipsDead = this.players.get(1).getShips().size() - secondRef.players.get(1).getShips().size();
        //int ennemyShipsDead = this.players.get(0).getShips().size() - secondRef.players.get(0).getShips().size();

        score += myShipsDead*(-10000);
        //score += ennemyShipsDead*1000;

        List<Ship> shipsBefore = this.players.get(1).getShipsAlive();
        List<Ship> shipsAfter = secondRef.players.get(1).getShipsAlive();

        for(int i=0; i<shipsBefore.size(); i++) {
            Ship currentShipBefore = shipsBefore.get(i);
            for(Ship currentShipAfter : shipsAfter){
                if(currentShipBefore.getId() == currentShipAfter.getId()){

                    //System.err.println("currentShipAfter:" + currentShipAfter.getHealth() + " currentShipBefore:" + currentShipBefore.getHealth());

                    //score += (currentShipAfter.getHealth() - currentShipBefore.getHealth())*10;
                    //score += currentShipAfter.distanceTo(currentShipBefore)*50;

                    // Less score if we are far away from the nearest barrel (not good idea)
                    /*if(barrels.size()>0) {
                        score -= currentShipAfter.distanceTo(currentShipAfter.getNearestEntity((List<Entity>)(List<?>)barrels));
                    }*/

                    if(currentShipAfter.distanceTo(currentShipBefore) <=3) {
                        score-=500;
                    }
                    break;
                }
            }

        }




        //Not use because of a bug ?
        /*List<Ship> ennemyShipsBefore = this.players.get(0).getShips();
        List<Ship> ennemyShipsAfter = secondRef.players.get(0).getShips();

        for(int i=0; i<ennemyShipsBefore.size(); i++) {
            Ship currentShipBefore = ennemyShipsBefore.get(i);
            for(Ship currentShipAfter : ennemyShipsAfter){
                if(currentShipBefore.getId() == currentShipAfter.getId()){
                    //score += (currentShipBefore.getHealth() - currentShipAfter.getHealth());
                    break;
                }
            }
        }*/

        score += myHealtWin*10;
        score -= ennemyHealtWin;

        //System.err.println("Score:" + score);
        return score;
    }

    protected void initReferee(int playerCount, Properties prop) {
        seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
        random = new Random(this.seed);

        shipsPerPlayer = clamp(
                Integer.valueOf(prop.getProperty("shipsPerPlayer", String.valueOf(random.nextInt(1 + MAX_SHIPS - MIN_SHIPS) + MIN_SHIPS))), MIN_SHIPS,
                MAX_SHIPS);

        if (MAX_MINES > MIN_MINES) {
            mineCount = clamp(Integer.valueOf(prop.getProperty("mineCount", String.valueOf(random.nextInt(MAX_MINES - MIN_MINES) + MIN_MINES))),
                    MIN_MINES, MAX_MINES);
        } else {
            mineCount = MIN_MINES;
        }

        barrelCount = clamp(
                Integer.valueOf(prop.getProperty("barrelCount", String.valueOf(random.nextInt(MAX_RUM_BARRELS - MIN_RUM_BARRELS) + MIN_RUM_BARRELS))),
                MIN_RUM_BARRELS, MAX_RUM_BARRELS);

        cannonballs = new ArrayList<>();
        cannonBallExplosions = new ArrayList<>();
        damage = new ArrayList<>();
        shipLosts = new ArrayList<>();

        // Generate Players
        this.players = new ArrayList<Player>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            this.players.add(new Player(i));
        }
        // Generate Ships
        for (int j = 0; j < shipsPerPlayer; j++) {
            int xMin = 1 + j * MAP_WIDTH / shipsPerPlayer;
            int xMax = (j + 1) * MAP_WIDTH / shipsPerPlayer - 2;

            int y = 1 + random.nextInt(MAP_HEIGHT / 2 - 2);
            int x = xMin + random.nextInt(1 + xMax - xMin);
            int orientation = random.nextInt(6);

            Ship ship0 = new Ship(x, y, orientation, 0);
            Ship ship1 = new Ship(x, MAP_HEIGHT - 1 - y, (6 - orientation) % 6, 1);

            this.players.get(0).ships.add(ship0);
            this.players.get(1).ships.add(ship1);
            this.players.get(0).shipsAlive.add(ship0);
            this.players.get(1).shipsAlive.add(ship1);
        }

        this.ships = players.stream().map(p -> p.ships).flatMap(List::stream).collect(Collectors.toList());

        // Generate mines
        mines = new ArrayList<>();
        while (mines.size() < mineCount) {
            int x = 1 + random.nextInt(MAP_WIDTH - 2);
            int y = 1 + random.nextInt(MAP_HEIGHT / 2);

            Mine m = new Mine(x, y);
            boolean valid = true;
            for (Ship ship : this.ships) {
                if (ship.at(m.position)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                if (y != MAP_HEIGHT - 1 - y) {
                    mines.add(new Mine(x, MAP_HEIGHT - 1 - y));
                }
                mines.add(m);
            }
        }
        mineCount = mines.size();

        // Generate supplies
        barrels = new ArrayList<>();
        while (barrels.size() < barrelCount) {
            int x = 1 + random.nextInt(MAP_WIDTH - 2);
            int y = 1 + random.nextInt(MAP_HEIGHT / 2);
            int h = MIN_RUM_BARREL_VALUE + random.nextInt(1 + MAX_RUM_BARREL_VALUE - MIN_RUM_BARREL_VALUE);

            RumBarrel m = new RumBarrel(x, y, h);
            boolean valid = true;
            for (Ship ship : this.ships) {
                if (ship.at(m.position)) {
                    valid = false;
                    break;
                }
            }
            for (Mine mine : this.mines) {
                if (mine.position.equals(m.position)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                if (y != MAP_HEIGHT - 1 - y) {
                    barrels.add(new RumBarrel(x, MAP_HEIGHT - 1 - y, h));
                }
                barrels.add(m);
            }
        }

    }


    protected void initReferee2() {

        ships = new ArrayList<>();
        damage= new ArrayList<>();
        shipLosts= new ArrayList<>();
        cannonBallExplosions= new ArrayList<>();
    }

    protected void updateReferee2(List<Player> players, List<Entity> mines, List<Entity> rumBarrels, List<Entity> cannonballs) {

        this.players = players;
        this.mines = (List<Mine>)(List<?>)mines;
        this.barrels = (List<RumBarrel>)(List<?>)rumBarrels;
        this.cannonballs = (List<Cannonball>)(List<?>)cannonballs;
        this.ships.clear();

        for(Player player : players) {
            ships.addAll(player.ships);
            //System.err.println("Ships size: " + ships.size());
        }

    }


    protected Properties getConfiguration() {
        Properties prop = new Properties();
        prop.setProperty("seed", String.valueOf(seed));
        prop.setProperty("shipsPerPlayer", String.valueOf(shipsPerPlayer));
        prop.setProperty("barrelCount", String.valueOf(barrelCount));
        prop.setProperty("mineCount", String.valueOf(mineCount));
        return prop;
    }

    protected void prepare(int round) {
        for (Player player : players) {
            for (Ship ship : player.ships) {
                ship.action = null;
                ship.message = null;
            }
        }
        cannonBallExplosions.clear();
        damage.clear();
        shipLosts.clear();
    }

    public Coord getNearestEnnemiesCoord(int myPlayerId, int myShipNumber) {
        return  getNearestEnnemiesShip(myPlayerId,myShipNumber).position;
    }

    public Entity getNearestEnnemiesShip(int myPlayerId, int myShipNumber) {
        Ship myCurrentShip = players.get(myPlayerId).getShips().get(myShipNumber);
        List<Entity> ennemieShips = (List<Entity>)(List<?>)players.get(1-myPlayerId).ships;

        Entity nearestEntity = myCurrentShip.getNearestEntity(ennemieShips);

        return  nearestEntity;
    }

    /**
     * Get the position of the nearest ennemy in nbRound
     * @param myPlayerId
     * @param myShipNumber
     * @return
     */
    public Coord getNextNearestEnnemyPosition(int myPlayerId, int myShipNumber) {
        Entity shipShooter = players.get(myPlayerId).getShips().get(myShipNumber);
        Ship ennemyShip = (Ship) getNearestEnnemiesShip(myPlayerId, myShipNumber);

        int nbRound = 1 + (shipShooter.distanceTo(ennemyShip)) / 3;

        Coord nextCoord = ennemyShip.position.clone();

        for(int i=0; i<nbRound*ennemyShip.speed; i++) {

            nextCoord = nextCoord.neighbor(ennemyShip.orientation);
        }

        if(nextCoord.isInsideMap()) {
            return nextCoord;
        } else {
            return ennemyShip.position;
        }


    }

    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        return this.players.get(playerIdx).shipsAlive.size();
    }

    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs) throws Exception {
        Player player = this.players.get(playerIdx);

        try {
            int i = 0;
            for (String line : outputs) {
                Matcher matchWait = PLAYER_INPUT_WAIT_PATTERN.matcher(line);
                Matcher matchMove = PLAYER_INPUT_MOVE_PATTERN.matcher(line);
                Matcher matchFaster = PLAYER_INPUT_FASTER_PATTERN.matcher(line);
                Matcher matchSlower = PLAYER_INPUT_SLOWER_PATTERN.matcher(line);
                Matcher matchPort = PLAYER_INPUT_PORT_PATTERN.matcher(line);
                Matcher matchStarboard = PLAYER_INPUT_STARBOARD_PATTERN.matcher(line);
                Matcher matchFire = PLAYER_INPUT_FIRE_PATTERN.matcher(line);
                Matcher matchMine = PLAYER_INPUT_MINE_PATTERN.matcher(line);
                Ship ship = player.shipsAlive.get(i++);

                if (matchMove.matches()) {
                    int x = Integer.parseInt(matchMove.group("x"));
                    int y = Integer.parseInt(matchMove.group("y"));
                    ship.setMessage(matchMove.group("message"));
                    ship.moveTo(x, y);
                } else if (matchFaster.matches()) {
                    ship.setMessage(matchFaster.group("message"));
                    ship.faster();
                } else if (matchSlower.matches()) {
                    ship.setMessage(matchSlower.group("message"));
                    ship.slower();
                } else if (matchPort.matches()) {
                    ship.setMessage(matchPort.group("message"));
                    ship.port();
                } else if (matchStarboard.matches()) {
                    ship.setMessage(matchStarboard.group("message"));
                    ship.starboard();
                } else if (matchWait.matches()) {
                    ship.setMessage(matchWait.group("message"));
                } else if (matchMine.matches()) {
                    ship.setMessage(matchMine.group("message"));
                    ship.placeMine();
                } else if (matchFire.matches()) {
                    int x = Integer.parseInt(matchFire.group("x"));
                    int y = Integer.parseInt(matchFire.group("y"));
                    ship.setMessage(matchFire.group("message"));
                    ship.fire(x, y);
                } else {
                    System.err.println("INVALID ACTION " + line);
                    throw new Exception("A valid action");
                }
            }
        } catch (Exception e) {
            player.setDead();
            try {
                throw e;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    private void decrementRum() {
        for (Ship ship : ships) {
            ship.damage(1);
        }
    }

    private void moveCannonballs() {
        for (Iterator<Cannonball> it = cannonballs.iterator(); it.hasNext();) {
            Cannonball ball = it.next();
            if (ball.remainingTurns == 0) {
                it.remove();
                continue;
            } else if (ball.remainingTurns > 0) {
                ball.remainingTurns--;
            }

            if (ball.remainingTurns == 0) {
                //System.err.println("Explosion here:  " + ball.position.toString());
                cannonBallExplosions.add(ball.position);
            }
        }
    }

    private void applyActions() {
        for (Player player : players) {
            for (Ship ship : player.shipsAlive) {
                if (ship.mineCooldown > 0) {
                    ship.mineCooldown--;
                }
                if (ship.cannonCooldown > 0) {
                    ship.cannonCooldown--;
                }

                ship.newOrientation = ship.orientation;

                if (ship.action != null) {
                    switch (ship.action) {
                        case FASTER:
                            if (ship.speed < MAX_SHIP_SPEED) {
                                ship.speed++;
                            }
                            break;
                        case SLOWER:
                            if (ship.speed > 0) {
                                ship.speed--;
                            }
                            break;
                        case PORT:
                            ship.newOrientation = (ship.orientation + 1) % 6;
                            break;
                        case STARBOARD:
                            ship.newOrientation = (ship.orientation + 5) % 6;
                            break;
                        case MINE:
                            if (ship.mineCooldown == 0) {
                                Coord target = ship.stern().neighbor((ship.orientation + 3) % 6);

                                if (target.isInsideMap()) {
                                    boolean cellIsFreeOfBarrels = barrels.stream().noneMatch(barrel -> barrel.position.equals(target));
                                    boolean cellIsFreeOfShips = ships.stream().filter(b -> b != ship).noneMatch(b -> b.at(target));

                                    if (cellIsFreeOfBarrels && cellIsFreeOfShips) {
                                        ship.mineCooldown = COOLDOWN_MINE;
                                        Mine mine = new Mine(target.x, target.y);
                                        mines.add(mine);
                                    }
                                }

                            }
                            break;
                        case FIRE:
                            int distance = ship.bow().distanceTo(ship.target);
                            if (ship.target.isInsideMap() && distance <= FIRE_DISTANCE_MAX && ship.cannonCooldown == 0) {
                                int travelTime = (int) (1 + Math.round(ship.bow().distanceTo(ship.target) / 3.0));
                                cannonballs.add(new Cannonball(ship.target.x, ship.target.y, ship.id, ship.bow().x, ship.bow().y, travelTime));
                                ship.cannonCooldown = COOLDOWN_CANNON;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private boolean checkCollisions(Ship ship) {
        Coord bow = ship.bow();
        Coord stern = ship.stern();
        Coord center = ship.position;

        // Collision with the barrels
        for (Iterator<RumBarrel> it = barrels.iterator(); it.hasNext();) {

            RumBarrel barrel = it.next();
            if (barrel.position.equals(bow) || barrel.position.equals(stern) || barrel.position.equals(center)) {

                /*if(players.get(1).shipsAlive.contains(ship)) {

                    System.err.println("COLLISION with barrels ------------------");

                    System.err.println("Barrel health " + barrel.health);
                }*/

                ship.heal(barrel.health);
                //String shipString = ship.toPlayerString(1);
                //System.err.println("SHIP STRING " + shipString);

                it.remove();
            }
        }

        // Collision with the mines
        for (Iterator<Mine> it = mines.iterator(); it.hasNext();) {
            Mine mine = it.next();
            List<Damage> mineDamage = mine.explode(ships, false);

            if (!mineDamage.isEmpty()) {

                //System.err.println("COLLISION with mines ------------------");
                damage.addAll(mineDamage);
                it.remove();
            }
        }

        return ship.health <= 0;
    }

    private void moveShips() {
        // ---
        // Go forward
        // ---
        for (int i = 1; i <= MAX_SHIP_SPEED; i++) {
            for (Player player : players) {
                for (Ship ship : player.shipsAlive) {
                    ship.newPosition = ship.position;
                    ship.newBowCoordinate = ship.bow();
                    ship.newSternCoordinate = ship.stern();

                    if (i > ship.speed) {
                        continue;
                    }

                    Coord newCoordinate = ship.position.neighbor(ship.orientation);

                    if (newCoordinate.isInsideMap()) {
                        // Set new coordinate.
                        ship.newPosition = newCoordinate;
                        ship.newBowCoordinate = newCoordinate.neighbor(ship.orientation);
                        ship.newSternCoordinate = newCoordinate.neighbor((ship.orientation + 3) % 6);
                    } else {
                        // Stop ship!
                        ship.speed = 0;
                    }
                }
            }

            // Check ship and obstacles collisions
            List<Ship> collisions = new ArrayList<>();
            boolean collisionDetected = true;
            while (collisionDetected) {
                collisionDetected = false;

                for (Ship ship : this.ships) {
                    if (ship.newBowIntersect(ships)) {
                        collisions.add(ship);
                    }
                }

                for (Ship ship : collisions) {
                    // Revert last move
                    ship.newPosition = ship.position;
                    ship.newBowCoordinate = ship.bow();
                    ship.newSternCoordinate = ship.stern();

                    // Stop ships
                    ship.speed = 0;

                    collisionDetected = true;
                }
                collisions.clear();
            }

            for (Player player : players) {
                for (Ship ship : player.shipsAlive) {
                    if (ship.health == 0) {
                        continue;
                    }

                    ship.position = ship.newPosition;
                    if (checkCollisions(ship)) {
                        shipLosts.add(ship);
                    }
                }
            }
        }
    }

    private void rotateShips() {
        // Rotate
        for (Player player : players) {
            for (Ship ship : player.shipsAlive) {
                ship.newPosition = ship.position;
                ship.newBowCoordinate = ship.newBow();
                ship.newSternCoordinate = ship.newStern();
            }
        }

        // Check collisions
        boolean collisionDetected = true;
        List<Ship> collisions = new ArrayList<>();
        while (collisionDetected) {
            collisionDetected = false;

            for (Ship ship : this.ships) {
                if (ship.newPositionsIntersect(ships)) {
                    collisions.add(ship);
                }
            }

            for (Ship ship : collisions) {
                ship.newOrientation = ship.orientation;
                ship.newBowCoordinate = ship.newBow();
                ship.newSternCoordinate = ship.newStern();
                ship.speed = 0;
                collisionDetected = true;
            }

            collisions.clear();
        }

        // Apply rotation
        for (Player player : players) {
            for (Ship ship : player.shipsAlive) {
                if (ship.health == 0) {
                    continue;
                }

                ship.orientation = ship.newOrientation;
                if (checkCollisions(ship)) {
                    shipLosts.add(ship);
                }
            }
        }
    }

    private boolean gameIsOver() {
        for (Player player : players) {
            if (player.shipsAlive.isEmpty()) {
                return true;
            }
        }
        return barrels.size() == 0 && LEAGUE_LEVEL == 0;
    }

    void explodeShips() {
        for (Iterator<Coord> it = cannonBallExplosions.iterator(); it.hasNext();) {
            Coord position = it.next();
            for (Ship ship : ships) {
                if (position.equals(ship.bow()) || position.equals(ship.stern())) {
                    //System.err.println("SHIP HIT !!! ");
                    damage.add(new Damage(position, LOW_DAMAGE, true));
                    ship.damage(LOW_DAMAGE);
                    it.remove();
                    break;
                } else if (position.equals(ship.position)) {
                    //System.err.println("SHIP HIT !!! ");
                    damage.add(new Damage(position, HIGH_DAMAGE, true));
                    ship.damage(HIGH_DAMAGE);
                    it.remove();
                    break;
                }
            }
        }
    }

    void explodeMines() {
        for (Iterator<Coord> itBall = cannonBallExplosions.iterator(); itBall.hasNext();) {
            Coord position = itBall.next();
            for (Iterator<Mine> it = mines.iterator(); it.hasNext();) {
                Mine mine = it.next();
                if (mine.position.equals(position)) {
                    damage.addAll(mine.explode(ships, true));
                    it.remove();
                    itBall.remove();
                    break;
                }
            }
        }
    }

    void explodeBarrels() {
        for (Iterator<Coord> itBall = cannonBallExplosions.iterator(); itBall.hasNext();) {
            Coord position = itBall.next();
            for (Iterator<RumBarrel> it = barrels.iterator(); it.hasNext();) {
                RumBarrel barrel = it.next();
                if (barrel.position.equals(position)) {
                    damage.add(new Damage(position, 0, true));
                    it.remove();
                    itBall.remove();
                    break;
                }
            }
        }
    }

    protected void updateGame(int round) throws  Exception {

        moveCannonballs();
        decrementRum();

        applyActions();
        moveShips();
        rotateShips();

        explodeShips();
        explodeMines();
        explodeBarrels();

        for (Ship ship : shipLosts) {
            barrels.add(new RumBarrel(ship.position.x, ship.position.y, REWARD_RUM_BARREL_VALUE));
        }

        for (Coord position : cannonBallExplosions) {
            damage.add(new Damage(position, 0, false));
        }

        for (Iterator<Ship> it = ships.iterator(); it.hasNext();) {
            Ship ship = it.next();
            if (ship.health <= 0) {
                //System.err.println("Ship: " + ship.getId() +" is dead !! ");
                players.get(ship.owner).shipsAlive.remove(ship);
                it.remove();
            }
        }

        if (gameIsOver()) {
            throw new Exception("endReached");
        }
    }

    protected void populateMessages(Properties p) {
        p.put("endReached", "End reached");
    }

    protected String[] getInitInputForPlayer(int playerIdx) {
        return new String[0];
    }

    protected String[] getInputForPlayer(int round, int playerIdx) {
        List<String> data = new ArrayList<>();

        // PlayerAgent's ships first
        for (Ship ship : players.get(playerIdx).shipsAlive) {
            data.add(ship.toPlayerString(playerIdx));
        }

        // Number of ships
        data.add(0, String.valueOf(data.size()));

        // Opponent's ships
        for (Ship ship : players.get((playerIdx + 1) % 2).shipsAlive) {
            data.add(ship.toPlayerString(playerIdx));
        }

        // Visible mines
        for (Mine mine : mines) {
            boolean visible = false;
            for (Ship ship : players.get(playerIdx).ships) {
                if (ship.position.distanceTo(mine.position) <= MINE_VISIBILITY_RANGE) {
                    visible = true;
                    break;
                }
            }
            if (visible) {
                data.add(mine.toPlayerString(playerIdx));
            }
        }

        for (Cannonball ball : cannonballs) {
            data.add(ball.toPlayerString(playerIdx));
        }

        for (RumBarrel barrel : barrels) {
            data.add(barrel.toPlayerString(playerIdx));
        }

        data.add(1, String.valueOf(data.size() - 1));

        return data.toArray(new String[data.size()]);
    }

    protected String[] getInitDataForView() {
        List<String> data = new ArrayList<>();

        data.add(join(MAP_WIDTH, MAP_HEIGHT, players.get(0).ships.size(), MINE_VISIBILITY_RANGE));

        data.add(0, String.valueOf(data.size() + 1));

        return data.toArray(new String[data.size()]);
    }

    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        List<String> data = new ArrayList<>();

        for (Player player : players) {
            data.addAll(player.toViewString());
        }
        data.add(String.valueOf(cannonballs.size()));
        for (Cannonball ball : cannonballs) {
            data.add(ball.toViewString());
        }
        data.add(String.valueOf(mines.size()));
        for (Mine mine : mines) {
            data.add(mine.toViewString());
        }
        data.add(String.valueOf(barrels.size()));
        for (RumBarrel barrel : barrels) {
            data.add(barrel.toViewString());
        }
        data.add(String.valueOf(damage.size()));
        for (Damage d : damage) {
            data.add(d.toViewString());
        }

        return data.toArray(new String[data.size()]);
    }

    protected String getGameName() {
        return "CodersOfTheCaribbean";
    }

    protected String getHeadlineAtGameStartForConsole() {
        return null;
    }

    protected int getMinimumPlayerCount() {
        return 2;
    }

    protected boolean showTooltips() {
        return true;
    }

    protected String[] getPlayerActions(int playerIdx, int round) {
        return new String[0];
    }

    protected boolean isPlayerDead(int playerIdx) {
        return false;
    }

    protected String getDeathReason(int playerIdx) {
        return "$" + playerIdx + ": Eliminated!";
    }

    protected int getScore(int playerIdx) {
        return players.get(playerIdx).getScore();
    }

    protected String[] getGameSummary(int round) {
        return new String[0];
    }

    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        players.get(playerIdx).setDead();
    }

    protected int getMaxRoundCount(int playerCount) {
        return 200;
    }

    protected int getMillisTimeForRound() {
        return 50;
    }

    public int getNumberOfShipsAlive(int playerId) {
        return players.get(playerId).getShipsAlive().size();
    }

    public void displayMap() {
        char map[][] = new char[MAP_HEIGHT][MAP_WIDTH];

        for(int i=0; i<MAP_HEIGHT; i++) {
            for(int j=0; j<MAP_WIDTH; j++) {
                map[i][j] = '.';
            }
        }
/*        private List<Cannonball> cannonballs;
        private List<Mine> mines;
        private List<RumBarrel> barrels;
        private List<Player> players;
        private List<Ship> ships;
        private List<Damage> damage;
        private List<Ship> shipLosts;
        private List<Coord> cannonBallExplosions;*/

        for(Mine mine : this.mines) {
            Coord coord = mine.position;
            map[coord.getY()][coord.getX()] = 'M';
        }


        for(Ship ship : this.ships) {
            Coord coord = ship.position;
            map[coord.getY()][coord.getX()] = 'S';
        }

        for(RumBarrel rumBarrel : this.barrels) {
            Coord coord = rumBarrel.position;
            map[coord.getY()][coord.getX()] = 'R';
        }

        for(Cannonball cannonball : this.cannonballs) {
            Coord coord = cannonball.position;
            map[coord.getY()][coord.getX()] = 'C';
        }

        for(int i=0; i<MAP_HEIGHT; i++) {
            System.out.println(map[i]);
        }
    }
}