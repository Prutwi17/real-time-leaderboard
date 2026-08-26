const XLSX = require('xlsx');
const http = require('http');

const EXCEL_PATH = process.argv[2];
const AUTH_SVC = 'http://localhost:8081';
const USER_SVC = 'http://localhost:8082';
const SCORE_SVC = 'http://localhost:8084';
const SPORT_SVC = 'http://localhost:8083';
const LEADERBOARD_SVC = 'http://localhost:8085';
const IMPORT_PASSWORD = 'Import@1234';

if (!EXCEL_PATH) {
  console.error('Usage: node import-players.cjs <EXCEL_FILE_PATH>');
  process.exit(1);
}

function request(method, urlStr, body, token) {
  return new Promise((resolve, reject) => {
    const url = new URL(urlStr);
    const postData = body ? JSON.stringify(body) : null;
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method,
      headers: { 'Content-Type': 'application/json' },
    };
    if (token) options.headers['Authorization'] = `Bearer ${token}`;
    if (postData) options.headers['Content-Length'] = Buffer.byteLength(postData);
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: JSON.parse(data) }); }
        catch { resolve({ status: res.statusCode, data }); }
      });
    });
    req.on('error', reject);
    req.setTimeout(30000, () => { req.destroy(); reject(new Error('timeout')); });
    if (postData) req.write(postData);
    req.end();
  });
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function slugify(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
}

async function main() {
  console.log('========================================');
  console.log('SPORTX PLAYER IMPORT TOOL v2');
  console.log('========================================\n');

  const wb = XLSX.readFile(EXCEL_PATH);
  const sheets = wb.SheetNames.filter(s => s !== 'Instructions');
  console.log('Sheets:', sheets);

  const football = XLSX.utils.sheet_to_json(wb.Sheets['Football'], { defval: '' });
  const cricket = XLSX.utils.sheet_to_json(wb.Sheets['Cricket'], { defval: '' });
  const f1 = XLSX.utils.sheet_to_json(wb.Sheets['F1'], { defval: '' });

  console.log('\n--- Dataset Counts ---');
  console.log('Football:', football.length, '(expected 20)');
  console.log('Cricket:', cricket.length, '(expected 22)');
  console.log('F1:', f1.length, '(expected 20)');
  console.log('Total:', football.length + cricket.length + f1.length);

  if (football.length !== 20 || cricket.length !== 22 || f1.length !== 20) {
    console.error('COUNT MISMATCH - aborting');
    process.exit(1);
  }

  let invalid = 0;
  const players = [];
  const seenUsernames = new Set();

  function addPlayers(data, sport) {
    for (const p of data) {
      const username = `sportx_${slugify(p.Name)}`;
      if (seenUsernames.has(username)) { console.error(`  DUPLICATE: ${p.Name}`); invalid++; continue; }
      seenUsernames.add(username);

      let bio = p['Short Bio'] || '';
      if (sport === 'FOOTBALL') {
        bio = [`Position: ${p.Position}`, `Team: ${p.Team}`, `Country: ${p.Country}`].filter(Boolean).join(' | ') + (bio ? `. ${bio}` : '');
      } else if (sport === 'CRICKET') {
        bio = [`Role: ${p.Role}`, `Batting: ${p['Batting Style']}`, `Bowling: ${p['Bowling Style']}`, `Team: ${p.Team}`, `Country: ${p.Country}`].filter(Boolean).join(' | ') + (bio ? `. ${bio}` : '');
      } else {
        bio = [`Car #${p['Car Number']}`, `Team: ${p.Team}`, `Nationality: ${p.Nationality}`].filter(Boolean).join(' | ') + (bio ? `. ${bio}` : '');
      }

      players.push({
        name: p.Name, username, score: Number(p['Initial Leaderboard Score']),
        sport, bio: bio.substring(0, 500),
        imageUrl: p['Profile Image URL'] || undefined,
        email: `${username}@sportx.import.local`,
      });
    }
  }

  addPlayers(football, 'FOOTBALL');
  addPlayers(cricket, 'CRICKET');
  addPlayers(f1, 'F1');

  if (invalid > 0) { console.error('VALIDATION FAILED'); process.exit(1); }
  console.log('\nAll', players.length, 'players validated OK');

  const sportsRes = await request('GET', `${SPORT_SVC}/api/sports`);
  const sportMap = {};
  for (const s of sportsRes.data) sportMap[s.code] = s.id;
  console.log('Sport IDs:', sportMap);

  // --- Check existing auth users ---
  console.log('\n--- Checking Existing Auth Users ---');
  let authRegistered = 0, authSkipped = 0, authFailed = 0;
  const usernameToUserId = {};

  for (const p of players) {
    const loginRes = await request('POST', `${AUTH_SVC}/api/auth/login`, { username: p.username, password: IMPORT_PASSWORD });
    if (loginRes.status === 200 && loginRes.data.accessToken) {
      usernameToUserId[p.username] = loginRes.data.userId;
      authSkipped++;
      continue;
    }

    const regRes = await request('POST', `${AUTH_SVC}/api/auth/register`, {
      username: p.username, email: p.email, password: IMPORT_PASSWORD, displayName: p.name,
    });
    if (regRes.status === 201) {
      const loginAfter = await request('POST', `${AUTH_SVC}/api/auth/login`, { username: p.username, password: IMPORT_PASSWORD });
      usernameToUserId[p.username] = loginAfter.data.userId;
      authRegistered++;
    } else if (regRes.status === 409) {
      const loginAfter = await request('POST', `${AUTH_SVC}/api/auth/login`, { username: p.username, password: IMPORT_PASSWORD });
      usernameToUserId[p.username] = loginAfter.data.userId;
      authSkipped++;
    } else {
      console.error(`  AUTH FAIL ${p.name}: ${regRes.status} ${JSON.stringify(regRes.data).substring(0, 80)}`);
      authFailed++;
    }
  }
  console.log(`Auth: registered=${authRegistered} existed=${authSkipped} failed=${authFailed}`);

  // --- Create Player Profiles ---
  console.log('\n--- Creating Player Profiles ---');
  const adminLogin = await request('POST', `${AUTH_SVC}/api/auth/login`, { username: 'feTestUser', password: 'Test@1234' });
  const adminToken = adminLogin.data.accessToken;

  let profileCreated = 0, profileSkipped = 0, profileFailed = 0;
  const existRes = await request('GET', `${USER_SVC}/api/players?page=0&size=500`);
  const existingPlayers = existRes.data.items || existRes.data.content || [];
  const existingEmails = new Set(existingPlayers.map(p => p.email));

  for (const p of players) {
    if (existingEmails.has(p.email)) { profileSkipped++; continue; }
    const res = await request('POST', `${USER_SVC}/api/players`, {
      displayName: p.name, email: p.email, bio: p.bio, profileImageUrl: p.imageUrl,
    }, adminToken);
    if (res.status === 201) profileCreated++;
    else if (res.status === 409) profileSkipped++;
    else {
      console.error(`  PROFILE FAIL ${p.name}: ${res.status}`);
      profileFailed++;
    }
  }
  console.log(`Profiles: created=${profileCreated} existed=${profileSkipped} failed=${profileFailed}`);

  // --- Submit Scores (each player uses their OWN JWT) ---
  console.log('\n--- Submitting Initial Scores ---');
  const scoreTypes = { FOOTBALL: 'POINTS', CRICKET: 'RUNS', F1: 'POSITION' };
  let scoresSubmitted = 0, scoresSkipped = 0, scoresFailed = 0;

  for (const p of players) {
    const authUserId = usernameToUserId[p.username];
    if (!authUserId) { console.error(`  SKIP (no auth): ${p.name}`); scoresSkipped++; continue; }
    const loginRes = await request('POST', `${AUTH_SVC}/api/auth/login`, { username: p.username, password: IMPORT_PASSWORD });
    if (loginRes.status !== 200 || !loginRes.data.accessToken) {
      console.error(`  LOGIN FAIL ${p.name}: ${loginRes.status}`); scoresFailed++; continue;
    }
    const playerToken = loginRes.data.accessToken;
    const subId = `sportx-${p.sport.toLowerCase()}-${slugify(p.name)}`;
    try {
      const res = await request('POST', `${SCORE_SVC}/api/scores`, {
        sportId: sportMap[p.sport], value: p.score,
        scoreType: scoreTypes[p.sport],
        eventName: `${p.sport} Initial Ranking`,
        submissionId: subId,
      }, playerToken);
      if (res.status === 201) { scoresSubmitted++; }
      else if (res.status === 409) { scoresSkipped++; }
      else {
        console.error(`  SCORE FAIL ${p.name}: ${res.status} ${JSON.stringify(res.data).substring(0, 80)}`);
        scoresFailed++;
      }
    } catch (e) {
      console.error(`  SCORE ERROR ${p.name}: ${e.message}`);
      scoresFailed++;
    }
    if (scoresSubmitted % 10 === 0 && scoresSubmitted > 0) {
      process.stdout.write(`  ...${scoresSubmitted} submitted\r`);
    }
  }
  console.log(`\nScores: submitted=${scoresSubmitted} skipped=${scoresSkipped} failed=${scoresFailed}`);

  console.log('\n--- Waiting 15s for Kafka -> Redis ---');
  await sleep(15000);

  // --- Verify ---
  console.log('\n--- Leaderboard Verification ---');
  const nameToAuthId = {};
  for (const p of players) nameToAuthId[p.name] = usernameToUserId[p.username];

  for (const sport of ['football', 'cricket', 'f1']) {
    const lb = await request('GET', `${LEADERBOARD_SVC}/api/leaderboards/${sport}/top?limit=25`);
    const entries = lb.data.entries || [];
    console.log(`\n${sport.toUpperCase()} (${entries.length} entries):`);
    for (const e of entries) {
      const name = Object.entries(nameToAuthId).find(([n, id]) => id === e.userId);
      console.log(`  #${e.rank} ${name ? name[0] : 'Player #' + e.userId} score=${e.score}`);
    }
  }

  console.log('\n--- Required Ranking Verification ---');
  const fbLb = await request('GET', `${LEADERBOARD_SVC}/api/leaderboards/football/top?limit=25`);
  const crEntry = (fbLb.data.entries || []).find(e => nameToAuthId['Cristiano Ronaldo'] === e.userId);
  const ckLb = await request('GET', `${LEADERBOARD_SVC}/api/leaderboards/cricket/top?limit=25`);
  const vkEntry = (ckLb.data.entries || []).find(e => nameToAuthId['Virat Kohli'] === e.userId);
  const abEntry = (ckLb.data.entries || []).find(e => nameToAuthId['AB de Villiers'] === e.userId);
  const f1Lb = await request('GET', `${LEADERBOARD_SVC}/api/leaderboards/f1/top?limit=25`);
  const mvEntry = (f1Lb.data.entries || []).find(e => nameToAuthId['Max Verstappen'] === e.userId);

  for (const [name, entry] of [['Cristiano Ronaldo', crEntry], ['Virat Kohli', vkEntry], ['AB de Villiers', abEntry], ['Max Verstappen', mvEntry]]) {
    if (entry) console.log(`  OK ${name}: rank=${entry.rank} score=${entry.score}`);
    else console.log(`  FAIL ${name}: NOT FOUND`);
  }

  console.log('\n========================================');
  console.log('IMPORT COMPLETE');
  console.log('========================================');
  console.log(`Auth: registered=${authRegistered} existed=${authSkipped} failed=${authFailed}`);
  console.log(`Profiles: created=${profileCreated} existed=${profileSkipped} failed=${profileFailed}`);
  console.log(`Scores: submitted=${scoresSubmitted} skipped=${scoresSkipped} failed=${scoresFailed}`);
}

main().catch(err => { console.error('FATAL:', err); process.exit(1); });
