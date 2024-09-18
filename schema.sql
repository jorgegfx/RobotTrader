-- phpMyAdmin SQL Dump
-- version 5.2.1deb3
-- https://www.phpmyadmin.net/
--
-- Host: localhost:3306
-- Generation Time: Sep 18, 2024 at 09:49 AM
-- Server version: 8.0.39-0ubuntu0.24.04.1
-- PHP Version: 8.3.6

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `robot_trading`
--

-- --------------------------------------------------------

--
-- Table structure for table `account`
--

CREATE TABLE `account` (
  `id` bigint NOT NULL,
  `name` varchar(45) DEFAULT NULL,
  `balance` double DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------

--
-- Table structure for table `fin_instrument`
--

CREATE TABLE `fin_instrument` (
  `symbol` varchar(20) NOT NULL,
  `name` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(10) NOT NULL,
  `volatility` double DEFAULT NULL,
  `exchange` varchar(20) NOT NULL,
  `creation_date` datetime NOT NULL,
  `last_update` datetime DEFAULT NULL,
  `active` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------

--
-- Table structure for table `position`
--

CREATE TABLE `position` (
  `id` bigint NOT NULL,
  `symbol` varchar(10) NOT NULL,
  `number_of_shares` int NOT NULL,
  `open_price_per_share` double NOT NULL,
  `close_price_per_share` double DEFAULT NULL,
  `open_date` datetime NOT NULL,
  `close_date` datetime DEFAULT NULL,
  `pnl` double DEFAULT NULL,
  `trading_strategy_type` varchar(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------

--
-- Table structure for table `trade_order`
--

CREATE TABLE `trade_order` (
  `id` varchar(40) NOT NULL,
  `type` varchar(5) NOT NULL,
  `symbol` varchar(20) NOT NULL,
  `date_time` datetime NOT NULL,
  `shares` int NOT NULL,
  `price` double NOT NULL,
  `trading_strategy_type` varchar(45) NOT NULL,
  `position_id` bigint DEFAULT NULL,
  `order_trigger` varchar(45) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------

--
-- Table structure for table `trading_exchange`
--

CREATE TABLE `trading_exchange` (
  `id` varchar(10) NOT NULL,
  `name` varchar(20) NOT NULL,
  `openingTime` time NOT NULL,
  `closingTime` time NOT NULL,
  `timezone` varchar(45) NOT NULL,
  `window_type` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------------------

--
-- Table structure for table `trading_strategy`
--

CREATE TABLE `trading_strategy` (
  `type` varchar(10) NOT NULL,
  `pnl` double DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `account`
--
ALTER TABLE `account`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `fin_instrument`
--
ALTER TABLE `fin_instrument`
  ADD PRIMARY KEY (`symbol`),
  ADD KEY `exchange_fk_idx` (`exchange`);

--
-- Indexes for table `position`
--
ALTER TABLE `position`
  ADD PRIMARY KEY (`id`),
  ADD KEY `instrument_fk_idx` (`symbol`);

--
-- Indexes for table `trade_order`
--
ALTER TABLE `trade_order`
  ADD PRIMARY KEY (`id`),
  ADD KEY `position_fk_idx` (`position_id`);

--
-- Indexes for table `trading_exchange`
--
ALTER TABLE `trading_exchange`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `trading_strategy`
--
ALTER TABLE `trading_strategy`
  ADD PRIMARY KEY (`type`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `account`
--
ALTER TABLE `account`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `position`
--
ALTER TABLE `position`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `fin_instrument`
--
ALTER TABLE `fin_instrument`
  ADD CONSTRAINT `exchange_fk` FOREIGN KEY (`exchange`) REFERENCES `trading_exchange` (`id`);

--
-- Constraints for table `position`
--
ALTER TABLE `position`
  ADD CONSTRAINT `instrument_fk` FOREIGN KEY (`symbol`) REFERENCES `fin_instrument` (`symbol`) ON DELETE RESTRICT ON UPDATE RESTRICT;

--
-- Constraints for table `trade_order`
--
ALTER TABLE `trade_order`
  ADD CONSTRAINT `position_fk` FOREIGN KEY (`position_id`) REFERENCES `position` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
