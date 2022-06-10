package com.mempoolexplorer.backend.services;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

import com.mempoolexplorer.backend.controllers.entities.RecalculateAllStatsResult;
import com.mempoolexplorer.backend.entities.algorithm.AlgorithmType;
import com.mempoolexplorer.backend.entities.ignored.IgnoringBlock;
import com.mempoolexplorer.backend.repositories.entities.MinerNameToBlockHeight;
import com.mempoolexplorer.backend.repositories.entities.MinerStatistics;
import com.mempoolexplorer.backend.repositories.reactive.IgBlockReactiveRepository;
import com.mempoolexplorer.backend.repositories.reactive.MinerNameToBlockHeightReactiveRepository;
import com.mempoolexplorer.backend.repositories.reactive.MinerStatisticsReactiveRepository;
import com.mempoolexplorer.backend.utils.SysProps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

	@Getter
	@Setter
	@AllArgsConstructor
	public class Data {
		private Long lostReward;
		private Optional<Long> ourAlgoFees;
	}

	@Autowired
	private IgBlockReactiveRepository igBlockReactiveRepository;

	@Autowired
	private MinerStatisticsReactiveRepository minerStatisticsRepository;

	@Autowired
	private MinerNameToBlockHeightReactiveRepository minerNameToBlockHeightRepository;

	private HashMap<Integer, Data> heightToGBTData = new HashMap<>();
	private HashMap<Integer, Data> heightToOBAData = new HashMap<>();

	@Override
	public RecalculateAllStatsResult recalculateAllStats() {
		RecalculateAllStatsResult res = new RecalculateAllStatsResult();

		minerStatisticsRepository.deleteAll().block();
		res.getExecutionInfoList().add("minerStatisticsRepository.deleteAll() executed.");
		minerNameToBlockHeightRepository.deleteAll().block();
		res.getExecutionInfoList().add("minerNameToBlockHeightRepository.deleteAll() executed.");

		igBlockReactiveRepository.findAll().doOnNext(ib -> saveStatistics(ib, res)).blockLast();

		return res;

	}

	private void saveStatistics(IgnoringBlock ib, RecalculateAllStatsResult res) {
		String minerName = ib.getMinedBlockData().getCoinBaseData().getMinerName();
		int blockHeight = ib.getMinedBlockData().getHeight();

		Optional<Long> opFees = ib.getMinedBlockData().getFeeableData().getTotalBaseFee();
		Optional<Long> opOurAlgoFees = ib.getCandidateBlockData().getFeeableData().getTotalBaseFee();

		AlgorithmType algorithmUsed = ib.getAlgorithmUsed();

		if (algorithmUsed == AlgorithmType.BITCOIND) {
			minerNameToBlockHeightRepository.save(
					new MinerNameToBlockHeight(minerName, blockHeight, ib.getMinedBlockData().getMedianMinedTime()))
					.block();

			Data obaData = heightToOBAData.remove(blockHeight);
			if (obaData != null) {
				saveMinerStatistics(minerName, blockHeight, ib.getLostReward(), obaData.getLostReward(), opFees, opFees);
				saveMinerStatistics(SysProps.GLOBAL_MINER_NAME, blockHeight, ib.getLostReward(), obaData.getLostReward(),
						opFees, opFees);
				saveMinerStatistics(SysProps.OUR_MINER_NAME, blockHeight, 0, 0, opOurAlgoFees, obaData.getOurAlgoFees());
			} else {
				heightToGBTData.put(blockHeight, new Data(ib.getLostReward(), opOurAlgoFees));
			}
		} else {// AlgorithmType.OURS
			Data gbtData = heightToGBTData.remove(blockHeight);
			if (gbtData != null) {
				saveMinerStatistics(minerName, blockHeight, gbtData.getLostReward(), ib.getLostReward(), opFees, opFees);
				saveMinerStatistics(SysProps.GLOBAL_MINER_NAME, blockHeight, gbtData.getLostReward(), ib.getLostReward(),
						opFees, opFees);
				saveMinerStatistics(SysProps.OUR_MINER_NAME, blockHeight, 0, 0, gbtData.getOurAlgoFees(), opOurAlgoFees);
			} else {
				heightToOBAData.put(blockHeight, new Data(ib.getLostReward(), opOurAlgoFees));
			}
		}
		res.getExecutionInfoList().add("Saved stats for block: " + blockHeight + ", Algorithm: " + algorithmUsed);
	}

	@Override
	public void saveStatisticsToDB(IgnoringBlock ibGBT, IgnoringBlock ibOBA) {

		String minerName = ibGBT.getMinedBlockData().getCoinBaseData().getMinerName();
		int height = ibGBT.getMinedBlockData().getHeight();

		Optional<Long> opFees = ibGBT.getMinedBlockData().getFeeableData().getTotalBaseFee();
		Optional<Long> opFeesGBT = ibGBT.getCandidateBlockData().getFeeableData().getTotalBaseFee();
		Optional<Long> opFeesOBA = ibOBA.getCandidateBlockData().getFeeableData().getTotalBaseFee();

		Instant medianMinedTime = ibGBT.getMinedBlockData().getMedianMinedTime();
		minerNameToBlockHeightRepository.save(new MinerNameToBlockHeight(minerName, height, medianMinedTime)).block();

		saveMinerStatistics(minerName, height, ibGBT.getLostReward(), ibOBA.getLostReward(), opFees, opFees);
		saveMinerStatistics(SysProps.GLOBAL_MINER_NAME, height, ibGBT.getLostReward(), ibOBA.getLostReward(), opFees,
				opFees);
		saveMinerStatistics(SysProps.OUR_MINER_NAME, height, 0, 0, opFeesGBT, opFeesOBA);

		log.info("Statistics persisted.");
	}

	private void saveMinerStatistics(String minerName, int blockHeight, long lostRewardGBT,
			long lostRewardOBA, Optional<Long> opFeesGBT, Optional<Long> opFeesOBA) {

		MinerStatistics minerStatistics = minerStatisticsRepository.findById(minerName).map(ms -> {
			ms.setNumBlocksMined(ms.getNumBlocksMined() + 1);
			ms.setTotalLostRewardGBT(ms.getTotalLostRewardGBT() + lostRewardGBT);
			ms.setTotalLostRewardOBA(ms.getTotalLostRewardOBA() + lostRewardOBA);
			ms.setTotalFeesGBT(ms.getTotalFeesGBT() + opFeesGBT.orElse(0L));
			ms.setTotalFeesOBA(ms.getTotalFeesOBA() + opFeesOBA.orElse(0L));
			// Avoid division by 0
			ms.setAvgLostRewardGBT(ms.getTotalLostRewardGBT() / Math.max(ms.getNumBlocksMined(), 1));
			ms.setAvgLostRewardOBA(ms.getTotalLostRewardOBA() / Math.max(ms.getNumBlocksMined(), 1));
			ms.setAvgFeesGBT(ms.getTotalFeesGBT() / Math.max(ms.getNumBlocksMined(), 1));
			ms.setAvgFeesOBA(ms.getTotalFeesOBA() / Math.max(ms.getNumBlocksMined(), 1));
			return ms;
		}).defaultIfEmpty(new MinerStatistics(minerName, 1, -1, lostRewardGBT, lostRewardOBA,
				lostRewardGBT, lostRewardOBA, opFeesGBT.orElse(0L), opFeesOBA.orElse(0L),
				opFeesGBT.orElse(0L), opFeesOBA.orElse(0L)))
				.block();

		// Only save if another instance has not done it yet.
		if (minerStatistics != null && minerStatistics.getLastMinedBlock() != blockHeight) {
			minerStatistics.setLastMinedBlock(blockHeight);
			minerStatisticsRepository.save(minerStatistics).block();
		}
	}

}
