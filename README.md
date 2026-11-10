# CricScore

A quick cricket scorer for street, gully and casual matches. Android 12+.

The scorer apps on the Play Store are full of ads, want an account, or both. This one
is free and open source (MIT), with no ads, no tracking, no analytics, no account and
no internet permission.

## Features

- **Setup in seconds**: team names, overs, players per side, who bats first. Player
  names are optional (blank means "Player 1", "Player 2", ...) and can be edited at
  any time, mid-match included (tap a name on the live screen, or use the menu).
  The form is prefilled from your last match.
- **Gully rules**: runs for a wide and for a no-ball (0, 1 or 2) and whether each is
  bowled again; free hit after a no-ball; last man standing; retiring a batter.
- **Ball-by-ball entry** with big buttons: 0 to 6, OUT (bowled, caught, LBW, run out,
  stumped, hit wicket), Wd, Nb (+ runs off the bat), Bye, LB.
  Run outs record who is out, the runs completed and the end where the wicket was
  broken, and can happen off a wide, no-ball, bye or leg bye.
- **Undo** any number of balls, including back into the first innings or out of a
  finished match.
- **Handled for you**: strike on odd runs and at the end of the over, re-bowled
  extras, next-batter and next-bowler prompts (suggesting the bowler from the other
  end), innings end on overs / all out / target reached, free hits, results
  including ties.
- **Live screen**: score, overs, CRR, target and RRR, runs needed, this over, striker,
  non-striker and bowler figures, current partnership.
- **Scorecard**: batting with dismissals, extras, yet to bat, fall of wickets,
  bowling figures with maidens, wides and no-balls, partnerships.
- **Share** the scorecard as an image or as text.
- **History**: every match is kept on the phone. The match in progress is saved after
  every ball, so it survives the app being killed.

## Permissions

None. The app has no `INTERNET` permission, so it cannot send anything anywhere.
Sharing an image goes through a `FileProvider` URI that grants the receiving app
read access to that one file, which needs no storage permission. See
[PRIVACY.md](PRIVACY.md).

## Building

```sh
# This machine has no system JDK; Android Studio's bundled JBR works.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease   # unsigned unless keystore.properties exists
```

Release signing reads an optional, git-ignored `keystore.properties` in the project
root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it the release
APK is built unsigned.

## Design decisions

- **Event sourcing.** A match is stored as the list of things the scorer tapped (`S`
  start innings, `D` delivery, `B` new batter, `O` new bowler, `R` retire, `X` swap
  strike, `E` end innings). Everything on screen (score, strike, overs, figures,
  partnerships, fall of wickets, target, result, and what to ask next) is derived by
  replaying that list in [`MatchEngine`](app/src/main/java/com/crylo/cricscore/engine/MatchEngine.kt).
  Undo is just dropping events off the end, so there is no inverse to get wrong for
  each action, and the engine is pure Kotlin covered by JVM tests in
  `app/src/test`. Replaying a whole match takes microseconds, so there is no caching.
- **Undo takes prompts with it.** Choosing the next batter or bowler is a response to a
  ball, not an action of its own, so undo removes it together with the ball that
  caused it. One tap of undo always means "un-bowl the last ball".
- **Run out ends.** Who faces next after a run out depends on the end where the wicket
  was broken, not on who was out, so the dialog asks for it. It defaults to the end the
  out batter was running towards, which is right most of the time.
- **Batters are positions, not a pointer.** The striker and non-striker slots are
  swapped on odd runs and at over end even when one is empty after a wicket; the new
  batter fills whichever slot is empty. That one rule gets "wicket off the last ball"
  right without special cases.
- **"All out" is about who is left, not a wicket count.** The innings ends when a crease
  is empty and nobody can come in (retired batters can return). With last man
  standing the survivor carries on alone and keeps the strike.
- **Gully simplifications.** Fielders are not recorded (dismissals read "ct b Bowler"),
  4 and 6 off the bat count as boundaries, the bowler stays fixed for the over, and
  there is no limit on overs per bowler. These keep entry to one or two taps per ball,
  which matters more on a street than scorebook detail.
- **Storage.** One Room row per match holds the setup and the events as text, one line
  per event, using enum names rather than ordinals so stored matches survive code
  changes. The row is rewritten after every ball: it's a few kilobytes, and a single
  row write keeps setup and events consistent if the process is killed. Room schemas
  are exported to `app/schemas` so future changes can ship as real migrations.
- **Share image** records the scorecard into a Compose graphics layer while it is drawn,
  so the image includes the part scrolled off screen without a second layout pass.

## License

[MIT](LICENSE)
