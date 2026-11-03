ALTER TABLE metal_saved_quotation
 ADD snapshot_version TINYINT NOT NULL DEFAULT 1,
 ADD factory_name_snapshot VARCHAR(100) NULL,
 ADD content_hash CHAR(64) NULL,
 ADD CONSTRAINT chk_quotation_snapshot_version CHECK(snapshot_version IN (1,2)),
 ADD UNIQUE KEY uk_quotation_content(factory_id,request_actor,content_hash);

CREATE TABLE metal_quotation_request (
 request_id VARCHAR(36) NOT NULL PRIMARY KEY,
 request_hash CHAR(64) NOT NULL,
 request_actor BIGINT NOT NULL,
 quote_id BIGINT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_quotation_request_quote FOREIGN KEY(quote_id) REFERENCES metal_saved_quotation(quote_id)
);

CREATE TABLE metal_quotation_image (
 quote_id BIGINT NOT NULL,
 item_index INT NOT NULL,
 image_png MEDIUMBLOB NOT NULL,
 image_hash CHAR(64) NOT NULL,
 renderer_version INT NOT NULL,
 PRIMARY KEY(quote_id,item_index),
 CONSTRAINT fk_quotation_image_quote FOREIGN KEY(quote_id) REFERENCES metal_saved_quotation(quote_id),
 CONSTRAINT chk_quotation_image_index CHECK(item_index BETWEEN 1 AND 100)
);
