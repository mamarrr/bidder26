# Playtech Internship Home Assignment - Summer 2026

## The Attention Economy Bid

The driving force behind developing internet communications and the growth of data
bandwidth over the last couple of decades has been video consumption. The more astute of
you would recognize YouTube (or TikTok, or both). What pays for such giant services is
advertising, most commonly presented before a video begins.
Before anything is shown on the screen, an auction takes place in which companies bid to have
their ads shown to the potential target audience.

## The Task - PvP

Unlike any other home assignment, this spring you will be battling against all participants at the
same time. You shall implement a bot to bid on video advertising. Your bot will compete directly
against all other applicants’ bots for advertising opportunities.

### Rules:

Each participant starts with 10 million credits (hereafter known as ebucks). The amount of
ebucks can be adjusted by a command line parameter. Ebucks are always an integer and do not
support fractions.
Each advertisement is evaluated internally by the bidding platform to calculate ‘value’ (points)
based on the viewer and the video. The bidding process contains hidden information about how
the value (points) of video ads and viewers is calculated. While the formula for the value is
based on real world data, it won't be disclosed directly.
The final score is calculated by the sum of the value of each advertisement played (won) divided
by the total ebucks spent, but no less than 30% of the provided budget. That means spending
very little would not be beneficial. The final ranking is solely based on the achieved score - the
higher the score, the higher the ranking.
The model of the auction consists of three main components: Category, Video, and Viewer. With
properties:

- Categories: Music, Sports, Kids, DIY, Video Games, ASMR, Beauty, Cooking, Finance
- Video: Category, View count, Comment count
- Viewer: subscribed, age, gender, “interests” (they map to the same categories above)

The participants must choose a category they would be advertising on. The bots must send their
chosen category as the first line. The category cannot be changed later.
At the end of each video bidding round, you Lose or Win [space] cost in ebucks in case of a win.
E.g. “W 11” or “L”
Winning a bid round grants points - the final ranking of all contestants is based on the points
won versus the ebucks spent.
Every 100 videos played, a summary of one’s accumulated points and the ebucks spent would
be provided. At that moment, it’s wise to evaluate the intended bidding and re-adjust. The
bidding continues until a set number of videos have been played or fewer than 10% of
participants have unspent ebucks. For the purpose of this early termination check, any
participant whose remaining ebucks drop below 2% of their initial budget is considered to have
spent all their ebucks.. Unless everyone has spent their ebucks at least 10% of the total set of
videos would always be played.

### Bidding:

- Provide a starting and max bid, e.g., 2 5. A higher starting bid takes priority in the event
of an equal max bid. If multiple participants have the same start/max bid, a weighted
random based on response times (in milliseconds) is chosen - the faster the response,
the more likely it is to be picked.

### Protocol:

I/O communication is entirely based on standard input and output (stdin and stdout). There are
no other means to transfer data. The protocol is fairly simple; each command is represented by
a single line terminated by a line feed (\n)

- The bot starts with a chosen category (mentioned above). It happens once only, and it
cannot be changed. Example:

```text
ASMR
```

A repeated loop (video watchin’, ads poppin’):

- The bidding platform sends information about the video and the viewer. The bot should
read it through the standard input.
- The bot sends a start and a max bid in ebucks (no fractions). Separated by a white space
(space or tab \t). The maximum allowed time to process the bidding information and
respond is limited to 40ms. Exceeding that time would result in assuming a max bid of
zero, effectively forfeiting the advertising possibility associated with the video. Bids over
the remaining ebucks would be ignored. The format is {startBid} {maxBid}. Bots must
respond to each round (if they have remaining funds).

Example:

```text
5 51
```

- The bidding platform sends a Win or Lose to each participant, along with the ebucks
spent to the winner. Example:

```text
L
W 12
```

- Every 100 rounds of videos and bids, the bidding platform sends a summary of the
points accumulated for those 100 videos, along with ebucks spent. The format is: S
{points} {ebucks}.

Example:

```text
S 1289 199
```

- The process continues until a set number of videos (greater than 100 000) have been
played or fewer than 10% of participants have unspent ebucks. Failing to spend ebucks
for an extended period results in the operation being terminated, as all remaining
participants have effectively failed to capture a sufficient share of viewers.

The bot input follows the format of “{field}=value(,)...\n”

### Fields:

- video.category - main category for the video, (loosely) matching the category of
the advertising is more likely to drive engagement
- video.viewCount - video count, the view count itself is not necessarily indicative
for the value of shown advertisement, the distribution of view count in videos
follows the power law, with view counts ranging up to 97 millions. More info: [0]
- video.commentCount - a higher ratio of commentCount/viewCount suggests
higher engagement with the video
- viewer.subscribed - Y / N Whether the viewer is subscribed to the channel,
subscriptions result in higher value
- viewer.age Age bracket, possible values: 13-17, 18-24, 25-34, 35-44, 45-54, 55+
- viewer.gender M / F Viewer gender, to simplify the algorithm, the gender is
always present
- viewer.interests 1–3 interests - the categories are picked from the videos
category list, semicolon-separated, ordered by relevance

Format example of video to bid for ads:

```text
video.viewCount=12345,video.category=Kids,video.commentCount=987,view
er.subscribed=Y,viewer.age=18-24,viewer.gender=F,viewer.interests=Vid
eo Games;Music
```

### Round-trip example

```text
→ ASMR
←video.category=Kids,video.viewCount=12345,video.commentCount=987,viewer.subscri
bed=Y,viewer.age=25-34,viewer.gender=F,viewer.interests=Video Games;Music
→ 5 51
← W 12
←
video.category=Music,video.viewCount=804213,video.commentCount=4511,viewer.subscr
ibed=N,viewer.age=18-24,viewer.gender=M,viewer.interests=Music;ASMR;Sports
→ 10 30
← L
 ... (98 more rounds) ...
← S 1289 199
← video.category=Sports,video.viewCount=...
 ... (continues) ...
```

### Requirements:

A Java application - any version of Java up to 25 is fine. Only the standard JDK library should be
used; no 3rd-party libraries, e.g., Spring, Logback, Lombok, etc. The application must not start
any additional threads, and it should not rely on the built-in fork/join mechanism either. Each
application would be limited to 192MB of heap. Access to any files (outside the
stdin/stdout/stderr) is prohibited. Sockets are considered files. Use stderr for logging if needed.
The application will be started with one parameter - the total amount of ebucks, e.g. main
method would have a String[] with length=1.

### Guidelines and hints:

Having a bot that complies with the protocol is obviously the most important part. We expect
only the source code of the application as a submission - either a public repository or an archive
(e.g., zip) sent to Playtech. Keep in mind that not spending ebucks still means 30% of the total
funds would be considered.
A sought after goal of this assignment is ensuring a low floor to enter but also a high ceiling to
compete for.
A test harness/bidding platform would be provided, with a bonus including two dumb bots. The
harness will be provided two days after this assignment will have been published.
Good luck and may the better bots prevail!

[0]: The relationship between view count and bracket value is not monotonic: it has peaks, dips,
and rebounds that reflect real advertising economics. A niche video can be worth more per
impression than a viral one. Discovering this value curve through experimentation is part of the
challenge.
