package runner;
import java.io.BufferedReader;
import java.io.IOException;

import bidder.BidCalculator;
import domain.Bid;
import domain.BidResult;
import domain.Category;
import domain.Summary;
import helpers.InputParser;
import helpers.DataParserResult;
import helpers.ProtocolFormatter;

public class Runner {
    

    public int points = 0;
    public int spentEBucks = 0;
    public int eBucks;
    public int startingEBucks;
    public BufferedReader reader;
    public Category category = Category.VIDEOGAMES;
    public InputParser parser;
    public BidCalculator bidCalculator;
    public ProtocolFormatter protocolFormatter;
    public boolean awaitingBidResult = false;
    

    public Runner(int startingEBucks, BufferedReader reader) {
        this.eBucks = startingEBucks;
        this.startingEBucks = startingEBucks;
        this.reader = reader;
        bidCalculator = new BidCalculator(startingEBucks);
        run();
    }

    public void run() {
        sendCategory();
        try {
            while (true) {
                String line = reader.readLine();

                if (line == null) {
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                if (line.startsWith("S")) {
                    Summary summary = InputParser.parseSummary(line);
                    processSummary(summary);
                    continue;
                }

                if (isBidResultPayload(line)) {
                    if (!awaitingBidResult) {
                    }
                    BidResult bidResult = InputParser.parseBidResult(line);
                    processBidResult(bidResult);
                    continue;
                }

                if (isVideoPayload(line)) {
                    if (awaitingBidResult) {
                        processBidResult(new BidResult(false, 0));
                    }
                    processVideoPayload(line);
                    continue;
                }


                if (awaitingBidResult) {
                    processBidResult(new BidResult(false, 0));
                }
                
            } 
    
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public void sendCategory() {
        String categoryString = category.getName();
        System.out.println(categoryString);
        System.out.flush();
    }

    public void sendBid(Bid bid) {
        String protocolBid = ProtocolFormatter.formatBid(bid);
        System.out.println(protocolBid);
        System.out.flush();
        awaitingBidResult = true;
    }

    public void processBidResult(BidResult bidResult) {
        eBucks -= bidResult.eBucksSpent();
        spentEBucks += bidResult.eBucksSpent();
        bidCalculator.onBidResult(bidResult.won(), bidResult.eBucksSpent());
        bidCalculator.setRemainingBudget(eBucks);
        awaitingBidResult = false;
    }

    public void processSummary(Summary summary) {
        
        points += summary.points();
        bidCalculator.onSummary(summary.points(), summary.eBucksSpent());
    }

    private boolean isVideoPayload(String line) {
        return line.startsWith("video.") || line.startsWith("viewer.");
    }

    private boolean isBidResultPayload(String line) {
        return line.startsWith("W") || line.startsWith("L");
    }

    private void processVideoPayload(String line) {
    
        DataParserResult parseResult = InputParser.readData(line);
        Bid bid = bidCalculator.calculateBid(parseResult);
        sendBid(bid);
    }
}
