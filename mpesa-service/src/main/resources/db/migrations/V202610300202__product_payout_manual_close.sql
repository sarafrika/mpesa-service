-- Finance may close a Tax Remittance or Pochi payment whose outcome stayed unknown as FAILED once the statement
-- shows nothing moved; who did it and their note are kept. A Safaricom result arriving later still settles it.
ALTER TABLE product_payouts ADD COLUMN closed_by VARCHAR(255);
ALTER TABLE product_payouts ADD COLUMN close_note VARCHAR(500);
