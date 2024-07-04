CREATE TABLE `position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `symbol` varchar(10) NOT NULL,
  `number_of_shares` int NOT NULL,
  `open_price_per_share` double NOT NULL,
  `close_price_per_share` double DEFAULT NULL,
  `open_date` datetime NOT NULL,
  `close_date` datetime DEFAULT NULL,
  `pnl` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci


CREATE TABLE `account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(45) DEFAULT NULL,
  `balance` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci

CREATE TABLE `fin_instr_config` (
  `symbol` varchar(10) NOT NULL,
  `pnl` double DEFAULT NULL,
  `type` varchar(10) NOT NULL,
  `strategy` varchar(10) NOT NULL,
  `last_pnl_update` datetime DEFAULT NULL,
  PRIMARY KEY (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci