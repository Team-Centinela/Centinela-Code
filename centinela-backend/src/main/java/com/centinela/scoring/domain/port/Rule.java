package com.centinela.scoring.domain.port;

import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.TransactionHistory;
import java.math.BigDecimal;
import java.util.List;

public interface Rule {
    String getRuleId();
    String getRuleName();
    int getPoints();
    RuleActivation evaluate(TransactionHistory current, List<TransactionHistory> history);
}
