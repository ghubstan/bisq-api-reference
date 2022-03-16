#!/bin/bash
source "env.sh"

# Warning:  Editing an offer involves removing it from the offer book, then re-publishing
# it with the changes.  This operation takes a few seconds and clients should not try
# to make rapid changes to the same offer;  there must be a delay before each edit request
# for the same offer.

# Disable an offer.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --enable=false

# Enable an offer.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --enable=true

# Edit the fixed-price, and/or change a market price margin based offer to a fixed-price offer.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --fixed-price=35000.5555

# Edit the price margin, and/or change a fixed-price offer to a market price margin based offer.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --market-price-margin=0.5

# Set the trigger price on a market price margin based offer.
# Note:  trigger prices do not apply to fixed-price offers.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --trigger-price=3960000.0000

# Remove the trigger price from a market price margin based offer.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --trigger-price=0

# Change a disabled fixed-price offer to a market price margin based offer, set a trigger price, and enable it.
$BISQ_HOME/bisq-cli --password=xyz --port=9998 editoffer \
  --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
  --market-price-margin=0.5 \
  --trigger-price=33000.0000 \
  --enable=true
