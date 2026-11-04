-- Only server-created orders after policy enablement are eligible.
ALTER TABLE metal_work_order ADD auto_semi_release_eligible BOOLEAN NOT NULL DEFAULT FALSE,
 ADD completion_generation INT NOT NULL DEFAULT 0;
CREATE TABLE metal_semi_auto_release_event (
 event_id BIGINT AUTO_INCREMENT PRIMARY KEY,work_order_id BIGINT NOT NULL,factory_id BIGINT NOT NULL,
 generation INT NOT NULL,actor_id BIGINT NOT NULL,status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 failures INT NOT NULL DEFAULT 0,next_attempt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 message VARCHAR(500),create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(work_order_id,generation),KEY(status,next_attempt),FOREIGN KEY(work_order_id) REFERENCES metal_work_order(work_order_id)
);
CREATE TABLE metal_semi_auto_release_result (
 event_id BIGINT NOT NULL,inventory_id BIGINT NOT NULL,status VARCHAR(24) NOT NULL,message VARCHAR(500),
 quantity BIGINT,net_weight DECIMAL(20,6),PRIMARY KEY(event_id,inventory_id),
 FOREIGN KEY(event_id) REFERENCES metal_semi_auto_release_event(event_id)
);
ALTER TABLE metal_semi_release ADD release_mode VARCHAR(8) NOT NULL DEFAULT 'MANUAL',
 ADD auto_event_id BIGINT NULL,ADD UNIQUE KEY uk_auto_event_inventory(auto_event_id,inventory_id),
 ADD FOREIGN KEY(auto_event_id) REFERENCES metal_semi_auto_release_event(event_id);
