const { Client } = require('pg');

const client = new Client({
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 5432,
  database: process.env.DB_NAME || 'hearintent',
  user: process.env.DB_USER || 'hearintent',
  password: process.env.DB_PASSWORD || 'hearintent',
});

async function listUsers() {
  try {
    await client.connect();
    console.log("Connected to database successfully.\\n");
    
    const res = await client.query('SELECT id, display_name, email, phone_number, created_at FROM users ORDER BY created_at DESC');
    
    if (res.rows.length === 0) {
      console.log("No users found in the database.");
    } else {
      console.log(`Found ${res.rows.length} registered user(s):\\n`);
      console.table(res.rows);
    }
  } catch (err) {
    console.error("Error connecting to database or querying users:", err.message);
  } finally {
    await client.end();
  }
}

listUsers();
