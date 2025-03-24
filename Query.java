package flightapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.spi.DirStateFactory.Result;
import java.util.*;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;

  private static final String NEXT_RESERVATION_ID_SQL = "SELECT count(*) AS num FROM reservations_ishaj83";
  private PreparedStatement nextReservationIDStmt;

  private static final String RESERVATION_SAME_DAY_SQL = "SELECT count(*) AS num FROM reservations_ishaj83 WHERE day_of_month = ? AND username = ?";
  private PreparedStatement reservationSameDayStmt;

  private static final String CHECK_CURR_FLIGHT1_CAP_SQL = "SELECT count(*) AS num FROM reservations_ishaj83 WHERE day_of_month = ? AND flightID1 = ?";
  private PreparedStatement flight1CapStmt;

  private static final String CHECK_CURR_FLIGHT2_CAP_SQL = "SELECT count(*) AS num FROM reservations_ishaj83 WHERE day_of_month = ? AND flightID2 = ?";
  private PreparedStatement flight2CapStmt;
  
  private static final String CHECK_RES_ID_EXISTS_SQL = "SELECT count(*) AS num FROM reservations_ishaj83 WHERE reservationID = ? AND paid = false AND username = ?";
  private PreparedStatement checkResIdStmt;

  private static final String CHECK_USER_BALANCE_SQL = "SELECT balance FROM users_ishaj83 WHERE username = ?";
  private PreparedStatement checkBalanceStmt;

  // Instance variables
  //

  private String loggedInUser;
  private List<Itinerary> itineraries;

  protected Query() throws SQLException, IOException {
    loggedInUser = null;
    itineraries = new ArrayList<>();
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      String clearTableUnsafe = "DELETE FROM reservations_ishaj83";
      PreparedStatement ps = conn.prepareStatement(clearTableUnsafe);
      int result = ps.executeUpdate();
      
      clearTableUnsafe = "DELETE FROM users_ishaj83";
      ps = conn.prepareStatement(clearTableUnsafe);
      result = ps.executeUpdate(); 
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    nextReservationIDStmt = conn.prepareStatement(NEXT_RESERVATION_ID_SQL);
    reservationSameDayStmt = conn.prepareStatement(RESERVATION_SAME_DAY_SQL);
    flight1CapStmt = conn.prepareStatement(CHECK_CURR_FLIGHT1_CAP_SQL);
    flight2CapStmt = conn.prepareStatement(CHECK_CURR_FLIGHT2_CAP_SQL);
    checkResIdStmt = conn.prepareStatement(CHECK_RES_ID_EXISTS_SQL);
    checkBalanceStmt = conn.prepareStatement(CHECK_USER_BALANCE_SQL);
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
   
    if(loggedInUser != null) {
      //if(!loggedInUser.equals(username))
        return "User already logged in\n";
    }
    String query = "SELECT * FROM users_ishaj83 WHERE username = ?";
    
    ResultSet rs = null;
    try {
      conn.setAutoCommit(false);

      PreparedStatement ps = conn.prepareStatement(query);
      ps.clearParameters();
      ps.setString(1, username);
      rs = ps.executeQuery();
      if(!rs.next()) {
        conn.rollback();
        //conn.setAutoCommit(true); // End transaction
        return "Login failed\n";
      }
      byte[] saltedHashedPW = rs.getBytes("hashedPassword"); 
      rs.close();
      ps.close();

      boolean match = PasswordUtils.plaintextMatchesSaltedHash(password, saltedHashedPW);
      if(!match) {
        conn.rollback();
        return "Login failed\n";
      }
      
      loggedInUser = username;
      conn.commit();
    } catch (SQLException e) {
        try {
            conn.rollback(); // Ensure rollback on failure
            if (isDeadlock(e)) {
                return transaction_login(username, password); // Retry on deadlock
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        e.printStackTrace();
    } finally {
        try {
            conn.setAutoCommit(true); // Restore default behavior
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    return "Logged in as " + loggedInUser + "\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    if(initAmount<0 || username.equals("")) {
      return "Failed to create user\n";
    }
    try {
      conn.setAutoCommit(false);

      String query = "SELECT username from users_ishaj83 WHERE username = ?";
      PreparedStatement ps = conn.prepareStatement(query);
      ps.clearParameters();
      ps.setString(1, username);
      ResultSet rs = ps.executeQuery();
      if(rs.next()) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }
      query = "INSERT INTO users_ishaj83 VALUES (?, ?, ?)";
      ps = conn.prepareStatement(query);
      ps.clearParameters();
      ps.setString(1, username); 
      ps.setBytes(2, PasswordUtils.saltAndHashPassword(password)); 
      ps.setInt(3, initAmount);
      int result = ps.executeUpdate();
      if(result == 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }
      conn.commit();
      conn.setAutoCommit(true);
      return "Created user " + username  + "\n";
    } catch (SQLException e) {
      try {
        conn.rollback();
        if(isDeadlock(e)) {
          return transaction_createCustomer(username, password, initAmount);
        } else {
          return "Failed to create user\n";
        }
      } catch(SQLException f) {
        f.printStackTrace();
        return "Failed to create user\n";
      }
    }
  }

  
  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // WARNING: the below code is insecure (it's susceptible to SQL injection attacks) AND only
    // handles searches for direct flights.  We are providing it *only* as an example of how
    // to use JDBC; you are required to replace it with your own secure implementation.
    //

    StringBuffer sb = new StringBuffer();
    int itineraryID = 0;
    itineraries.clear();
    try {
      // one hop itineraries
      
      String directSearchSQL = "SELECT * FROM Flights " 
        + "WHERE origin_city = ? AND dest_city = ? "
        + "AND day_of_month =  " + "?" + " "
        + "AND canceled != 1" + " "
        + "ORDER BY actual_time, fid ASC LIMIT ?";
      
      PreparedStatement ps = conn.prepareStatement(directSearchSQL);
      ps.clearParameters();
      
      ps.setString(1, originCity);
      ps.setString(2, destinationCity);
      ps.setInt(3, dayOfMonth);
      ps.setInt(4, numberOfItineraries);
      ResultSet oneHopResults = ps.executeQuery();
      
      while (oneHopResults.next() && itineraryID<numberOfItineraries) {
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("carrier_id");
        String result_flightNum = oneHopResults.getString("flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_time = oneHopResults.getInt("actual_time");
        int result_capacity = oneHopResults.getInt("capacity");
        int result_price = oneHopResults.getInt("price");
        int result_fid = oneHopResults.getInt("fid");
        Flight f = new Flight(result_fid, result_dayOfMonth, result_carrierId, result_flightNum, result_originCity, result_destCity, result_time, result_capacity, result_price);
        Itinerary i = new Itinerary(itineraryID, f, null, result_originCity, result_destCity, null, result_time);
        itineraries.add(i);
        itineraryID++;
      }
      
      oneHopResults.close();
    
      // indirect flights
      int i = numberOfItineraries - itineraryID;
      if(i>0 && !directFlight) {
        String indirectSearchSQL = "SELECT f1.fid AS f1_fid, f2.fid AS f2_fid, f1.origin_city AS origin, f1.dest_city AS stopover, f2.dest_city AS dest, f1.carrier_id AS f1_carrier_id, f2.carrier_id AS f2_carrier_id, f1.flight_num AS f1_flight_num, f2.flight_num AS f2_flight_num, f1.actual_time AS f1_actual_time,f2.actual_time AS f2_actual_time, f1.actual_time+f2.actual_time AS total_time, f1.capacity AS f1_capacity, f1.price AS f1_price, f2.capacity AS f2_capacity, f2.price AS f2_price FROM flights f1, flights f2 " 
        + "WHERE f1.origin_city= ? and f2.dest_city = ? AND f1.dest_city = f2.origin_city AND f1.day_of_month = ? AND f2.day_of_month = ? AND f1.canceled = 0 AND f2.canceled = 0 ORDER BY total_time, f1.fid, f2.fid ASC LIMIT ?;";

        ps = conn.prepareStatement(indirectSearchSQL);
        ps.clearParameters();
        ps.setString(1, originCity);
        ps.setString(2, destinationCity);
        ps.setInt(3, dayOfMonth);
        ps.setInt(4, dayOfMonth);
        ps.setInt(5, numberOfItineraries);

        ResultSet twoHopResults = ps.executeQuery();

        while(twoHopResults.next() && itineraryID<numberOfItineraries) {
          String f1_carrierId = twoHopResults.getString("f1_carrier_id");
          String f1_flightNum = twoHopResults.getString("f1_flight_num");
          String f1_originCity = twoHopResults.getString("origin");
          String f1_destCity = twoHopResults.getString("stopover");
          int f1_time = twoHopResults.getInt("f1_actual_time");
          int f1_capacity = twoHopResults.getInt("f1_capacity");
          int f1_price = twoHopResults.getInt("f1_price");
          int f1_fid = twoHopResults.getInt("f1_fid");

          String f2_carrierId = twoHopResults.getString("f2_carrier_id");
          String f2_flightNum = twoHopResults.getString("f2_flight_num");
          String f2_originCity = twoHopResults.getString("stopover");
          String f2_destCity = twoHopResults.getString("dest");
          int f2_time = twoHopResults.getInt("f2_actual_time");
          int f2_capacity = twoHopResults.getInt("f2_capacity");
          int f2_price = twoHopResults.getInt("f2_price");
          int f2_fid = twoHopResults.getInt("f2_fid");

          Flight f1 = new Flight(f1_fid, dayOfMonth, f1_carrierId, f1_flightNum, f1_originCity, f1_destCity, f1_time, f1_capacity, f1_price);
          Flight f2 = new Flight(f2_fid, dayOfMonth, f2_carrierId, f2_flightNum, f2_originCity, f2_destCity, f2_time, f2_capacity, f2_price);
          Itinerary it = new Itinerary(itineraryID, f1, f2, f1_originCity, f2_destCity, f1_destCity, f1_time+f2_time);
          itineraries.add(it);
          itineraryID++;
        }
      } 
      if(itineraryID==0) {
        return "No flights match your selection\n";
      }
      Collections.sort(itineraries, (i1, i2) -> i1.totalTime-i2.totalTime);
      int n = 0;
      for(Itinerary itinerary : itineraries) {
        itinerary.setId(n);
        sb.append(itinerary.toString() + "\n");
        n++;
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to search\n";
    }
    return sb.toString();
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    try {
      // check if user is logged in
      if(loggedInUser == null)
        return "Cannot book reservations, not logged in\n";
      // user must enter a valid itinerary id returned from the most recent 
      // search performed within the same login session
      if(itineraries.isEmpty() || itineraryId < 0 || itineraryId >= itineraries.size())
        return "No such itinerary " +  itineraryId + "\n";
    
      Itinerary i = itineraries.get(itineraryId);
      if(i == null) 
        return "No such itinerary " +  itineraryId + "\n";

      conn.setAutoCommit(false);
      // check if reservation already exists
      if(doesReservationExist(i.getDay())) {
        conn.rollback();
        return "You cannot book two flights in the same day\n";
      }

      // check flight capacities
      int flight1Cap = checkFlightCapacity(i.getFlight1ID());
      if(currFlightCapacity(i.getDay(), "flightID1", i.getFlight1ID()) >= flight1Cap) {
        conn.rollback();
        return "Booking failed\n";
      }

      if(i.getFlight2ID() != -1) {
        int flight2Cap = checkFlightCapacity(i.getFlight2ID());
        if(currFlightCapacity(i.getDay(), "flightID2", i.getFlight2ID()) >= flight2Cap) {
          conn.rollback();
          return "Booking failed\n";
        }
      }
      
      // book the itinerary
      String query = "INSERT INTO reservations_ishaj83 VALUES (?, ?, ?, ?, ?, ?)";
      PreparedStatement ps = conn.prepareStatement(query);
      ps.clearParameters();
      int resId = getNextResID();
      ps.setInt(1, resId); 
      ps.setBoolean(2, false);
      ps.setInt(3, i.getDay());
      ps.setString(4, loggedInUser);
      ps.setInt(5, i.getFlight1ID());
      if(i.getFlight2ID() == -1)
        ps.setNull(6, java.sql.Types.INTEGER);
      else
        ps.setInt(6, i.getFlight2ID());

      int result = ps.executeUpdate();
      
      if(result == 0) {
        conn.rollback();
        return "Booking failed\n";
      } 
      conn.commit();
      return "Booked flight(s), reservation ID: " + resId + "\n";
    } catch (SQLException e) {
        try {
          conn.rollback();
          if (isDeadlock(e)) {
            return transaction_book(itineraryId);
          }
        } catch (SQLException r) {
          r.printStackTrace();
        }
        e.printStackTrace();
        return "Booking failed\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    // check if user is logged in
    if(loggedInUser == null) {
      return "Cannot pay, not logged in\n";
    }
    try {
      
      conn.setAutoCommit(false);

      // check if reservation is not found, not under the logged-in user's name, or is already paid
      if(!foundUnpaidResGivenID((reservationId))) {
        conn.rollback();
        return "Cannot find unpaid reservation " + reservationId + " under user: " + loggedInUser + "\n";
      }

      // check if user doesn't have enough money in acct
      String resPriceQuery = "SELECT sum(f.price) AS totalPrice FROM Flights f, reservations_ishaj83 r WHERE f.fid = r.flightID1 OR f.fid = r.flightID2";
      PreparedStatement ps = conn.prepareStatement(resPriceQuery);
      ResultSet rs = ps.executeQuery();
      if(!rs.next()) {
        conn.rollback();
        return "Failed to pay for reservation " + reservationId + "\n";
      }
      int resPrice = rs.getInt("totalPrice");
      rs.close();
      int balance = getUserBalance();
      conn.rollback();
      if(balance < resPrice) {
        return "User has only " + balance + " in account but itinerary costs " + resPrice + "\n";
      }

      // update reservation paid column
      String updatePaidQuery = "UPDATE reservations_ishaj83 SET paid = true WHERE reservationID = ?";
      ps = conn.prepareStatement(updatePaidQuery);
      ps.clearParameters();
      ps.setInt(1, reservationId);
      ps.executeUpdate();
      
      // update user balance
      String updateBalanceQuery = "UPDATE users_ishaj83 SET balance = ? WHERE username = ?";
      ps = conn.prepareStatement(updateBalanceQuery);
      ps.clearParameters();
      ps.setInt(1, balance-resPrice);
      ps.setString(2, loggedInUser);
      ps.executeUpdate();

      conn.commit();
      return "Paid reservation: " + reservationId + " remaining balance: " + getUserBalance() + "\n";
    } catch (SQLException e) {
      try {
        conn.rollback();
        if (isDeadlock(e)) {
          return transaction_pay(reservationId);
        }
      } catch (SQLException r) {
        r.printStackTrace();
      }
      e.printStackTrace();
      return "Failed to pay for reservation " + reservationId + "\n";
    }

  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    StringBuffer sb = new StringBuffer();
    try {
      // check if user is logged in
      if(loggedInUser == null) {
        return "Cannot view reservations, not logged in\n";
      }
      conn.setAutoCommit(false);

      String query = "SELECT * FROM reservations_ishaj83 WHERE username = ?";
      PreparedStatement ps = conn.prepareStatement(query);
      ps.clearParameters();
      ps.setString(1, loggedInUser);
      ResultSet rs = ps.executeQuery();
      
      int count = 0;
      while(rs.next()) {
        boolean paid = rs.getBoolean("paid");
        int resId = rs.getInt("reservationID");
        int f1Id = rs.getInt("flightID1");
        int f2Id = rs.getInt("flightID2");
        Flight f1 = createFlight(f1Id);
        Flight f2 = null;
        if(f2Id != 0)
          f2 = createFlight(f2Id);
        sb.append("Reservation " + resId + " paid: " + paid + ":\n" + f1.toString() + "\n");
        if(f2 != null) {
          sb.append(f2.toString() + "\n");
        }
        count++;
      }
      rs.close();
      if(count == 0) {
        sb.append("No reservations found\n");
      }
      conn.commit();
      return sb.toString();
    // } catch(SQLException e) {
    //   e.printStackTrace();
    //   return "Failed to retrieve reservations\n";
    // }
    } catch (SQLException e) {
        try {
            conn.rollback(); // Rollback transaction on error
            if (isDeadlock(e)) {
                return transaction_reservations(); // Retry on deadlock
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        e.printStackTrace();
        return "Failed to retrieve reservations\n";
    } finally {
        try {
            conn.setAutoCommit(true); // Restore default behavior
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
  }

  private Flight createFlight(int fid) {
    try {
      Flight f = null;
      
      String query = "SELECT * FROM Flights WHERE fid = ?";
      
      PreparedStatement ps = conn.prepareStatement(query);
      ps.clearParameters();
      ps.setInt(1, fid);
      ResultSet rs = ps.executeQuery();
      
      if (rs.next()) {
        int result_dayOfMonth = rs.getInt("day_of_month");
        String result_carrierId = rs.getString("carrier_id");
        String result_flightNum = rs.getString("flight_num");
        String result_originCity = rs.getString("origin_city");
        String result_destCity = rs.getString("dest_city");
        int result_time = rs.getInt("actual_time");
        int result_capacity = rs.getInt("capacity");
        int result_price = rs.getInt("price");
        int result_fid = rs.getInt("fid");
        f = new Flight(result_fid, result_dayOfMonth, result_carrierId, result_flightNum, result_originCity, result_destCity, result_time, result_capacity, result_price);
      }
      rs.close();
      return f;
    } catch(SQLException e) {
      e.printStackTrace();
      return null;
    }
  }
  /**
   * Utility function to get a user's balance
   */
  private int getUserBalance() {
    try {
      checkBalanceStmt.clearParameters();
      checkBalanceStmt.setString(1, loggedInUser);

      ResultSet results = checkBalanceStmt.executeQuery();
      results.next();
      int balance = results.getInt("balance");
      results.close();

      return balance;
    } catch(SQLException e) {
      e.printStackTrace();
      return 0;
    }
    
  }

  /**
   * Utility function to determine whether an UNPAID reservation already exists for a user given the reservation id
   */
  private boolean foundUnpaidResGivenID(int reservationID) {
    try {
      checkResIdStmt.clearParameters();
      checkResIdStmt.setInt(1, reservationID);
      checkResIdStmt.setString(2, loggedInUser);

      ResultSet results = checkResIdStmt.executeQuery();
      results.next();
      int num = results.getInt("num");
      results.close();

      return num>0;
    } catch(SQLException e) {
      e.printStackTrace();
      return false;
    }
    
  }
  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine the next reservation id
   */
  private int getNextResID() throws SQLException {
    nextReservationIDStmt.clearParameters();

    ResultSet results = nextReservationIDStmt.executeQuery();
    results.next();
    int num = results.getInt("num");
    results.close();

    return num+1;
  }

  /**
   * Utility function to determine whether a reservation already exists for the user on a given day
   */
  private boolean doesReservationExist(int day_of_month) throws SQLException {
    reservationSameDayStmt.clearParameters();
    reservationSameDayStmt.setInt(1, day_of_month);
    reservationSameDayStmt.setString(2, loggedInUser);

    ResultSet results = reservationSameDayStmt.executeQuery();
    results.next();
    int num = results.getInt("num");
    results.close();

    return num>0;
  }

  /**
   * Utility function to determine the current number of seats booked in a flight so far
   */
  private int currFlightCapacity(int day_of_month, String flight, int fid) throws SQLException {
    ResultSet results = null;
    if(flight.equals("flightID1")) {
      flight1CapStmt.clearParameters();
      flight1CapStmt.setInt(1, day_of_month);
      flight1CapStmt.setInt(2, fid);
      results = flight1CapStmt.executeQuery();
    } else if(flight.equals("flightID2")) {
      flight2CapStmt.clearParameters();
      flight2CapStmt.setInt(1, day_of_month);
      flight2CapStmt.setInt(2, fid);
      results = flight2CapStmt.executeQuery();
    }
    if(results == null) return -1;
    results.next();
    int count = results.getInt("num");
    results.close();

    return count;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return "40001".equals(e.getSQLState()) || "40P01".equals(e.getSQLState());
  }

  class Itinerary {
    int id;
    Flight f1;
    Flight f2;
    String origin;
    String dest;
    String stopover;
    int totalTime;

    public Itinerary(int id, Flight f1, Flight f2, String origin, String dest, String stopover, int totalTime) {
      this.id = id;
      this.f1 = f1;
      this.f2 = f2;
      this.origin = origin;
      this.dest = dest;
      this.stopover = stopover;
      this.totalTime = totalTime;
    }

    private void setId(int id) {
      this.id = id;
    }

    private int getFlight1ID() {
      return f1.fid;
    }

    private int getFlight2ID() {
      if(f2 == null) return -1;
      return f2.fid;
    }

    private int getDay() {
      return f1.dayOfMonth;
    }

    private int getPrice() {
      if(f2 == null)
        return f1.price;
      else
        return f1.price + f2.price;
    }

    @Override
    public String toString() {
      int numFlights = f1 != null && f2 != null ? 2 : 1;
      String str = "Itinerary " + this.id + ": " + numFlights + " flight(s), " + totalTime + " minutes";
      if(numFlights==1)
        return str + "\n" + f1.toString();
      return str + "\n" + f1.toString() + "\n" + f2.toString();
    }
  }

  /**
   * A class to store information about a single flight
   *
   * TODO(hctang): move this into QueryAbstract
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
