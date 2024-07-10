use `robot_trading`;
CREATE TABLE `position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `symbol` varchar(10) NOT NULL,
  `number_of_shares` int NOT NULL,
  `open_price_per_share` double NOT NULL,
  `close_price_per_share` double DEFAULT NULL,
  `open_date` datetime NOT NULL,
  `close_date` datetime DEFAULT NULL,
  `pnl` double DEFAULT NULL,
  `tradingStrategyType` varchar(10) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1;


CREATE TABLE `account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(45) DEFAULT NULL,
  `balance` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TABLE `fin_instrument` (
  `symbol` varchar(10) NOT NULL,
  `type` varchar(10) NOT NULL,
  `volatility` double NOT NULL,
  `exchange` varchar(20) NOT NULL,
  `creation_date` datetime NOT NULL,
  PRIMARY KEY (`symbol`)
) ENGINE=InnoDB;

CREATE TABLE `trading_strategy` (
  `type` varchar(10) NOT NULL,
  `pnl` double DEFAULT NULL,
  PRIMARY KEY (`type`)
) ENGINE=InnoDB;