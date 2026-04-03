package helpers;

import domain.Bid;

public class ProtocolFormatter {
    
    public ProtocolFormatter() {

    }

    public static String formatBid(Bid bid) {
        int startBid = bid.startBid();
        int maxBid = bid.maxBid();

        return String.valueOf(startBid) + " " + String.valueOf(maxBid);
    }
}
