//
// atw-launch --online-readiness: checks, without starting the game, each link
// an authenticated server join depends on, and prints a redacted report:
//   1. config/account.dat exists and Windows (DPAPI) can decrypt it here;
//   2. the saved Minecraft token has time left;
//   3. Minecraft services accept the token and return the saved profile;
//   4. Mojang's session server accepts a join with the token, and hasJoined
//      confirms it. Online-mode servers such as Hypixel make this same check;
//   5. Lunar's accounts.json holds the same token as the active account, with
//      no refreshToken key (Lunar invalidates accounts that have one);
//   6. the last game log: whether Lunar set the session or invalidated the
//      account, and how often Lunar's assets authenticator answered 403.
// It never prints or writes tokens, never refreshes or saves the sign-in, and
// does not connect to any game server. Exit codes: 0 ready; 1 a check failed;
// 3 the saved token is expired or about to expire, which the next normal start
// refreshes before the game starts.
//

#ifndef ATW_AUTH_ONLINEREADINESS_H
#define ATW_AUTH_ONLINEREADINESS_H

int runOnlineReadiness();

#endif // ATW_AUTH_ONLINEREADINESS_H
