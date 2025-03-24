# flights-app
A CLI-based airline reservation system built in Java with PostgreSQL. Users can create accounts, search flights, book and pay for itineraries, and view reservations. Ensures data consistency with SQL transactions.

📂 Features
- User account creation & login
- Flight search (direct & indirect)
- Itinerary booking & payment
- Reservation management
- ACID-compliant SQL transaction handling
- Password hashing & salting for secure login

🛠 Tech Stack
- Java
- PostgreSQL
- Maven
- JDBC
- SQL Transactions


⚙️ Setup Instructions

1. Install PostgreSQL

2. Create a new database:

  CREATE DATABASE flights;

3. Load Flight Data

\copy CARRIERS from 'carriers.csv' CSV

\copy MONTHS from 'months.csv' CSV

\copy WEEKDAYS from 'weekdays.csv' CSV

\copy FLIGHTS from 'flights-small.csv' CSV


4. Set Up JDBC Connection

Configure dbconn.properties:

  flightapp.server_url = localhost
  
  flightapp.database_name = flights
  
  flightapp.username = your_postgres_username
  
  flightapp.password = your_postgres_password
  
  flightapp.tablename_suffix = your_UWNetID


5. Build and Run

  mvn clean compile assembly:single
  
  java -jar target/FlightApp-1.0-jar-with-dependencies.jar


✅ Example Commands

  create alice password123 1000

  login alice password123

  search Seattle Boston 0 3 5

  book 0

  pay 1

  reservations

  quit
