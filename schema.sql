CREATE TABLE `position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `symbol` varchar(10) NOT NULL,
  `number_of_shares` int NOT NULL,
  `open_price_per_share` double NOT NULL,
  `close_price_per_share` double DEFAULT NULL,
  `open_date` timestamp(5) NOT NULL,
  `close_date` timestamp(5) NULL DEFAULT NULL,
  `pnl` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci


CREATE TABLE `account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(45) DEFAULT NULL,
  `balance` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci