package com.cookandroid.ai_agent.domain;

import com.cookandroid.ai_agent.data.CheckInEntry;

import java.util.List;
import java.util.Locale;

public final class GuidanceEngine {

    private GuidanceEngine() {
    }

    public static String buildTodayHeadline(int score) {
        if (score >= 70) {
            return "최근 신호가 비교적 안정적입니다. 오늘 리듬을 크게 흔들지 않는 편이 좋습니다.";
        }
        if (score >= 45) {
            return "오늘은 무리하지 않으면 충분히 운영 가능한 범위입니다. 구조를 단순하게 유지하세요.";
        }
        return "오늘은 부담 신호가 크게 보입니다. 회복에 도움이 되는 선택을 우선하는 편이 좋습니다.";
    }

    public static String buildTodayInsight(CheckInEntry latestEntry, List<CheckInEntry> entries) {
        int latestScore = ScoreEngine.calculateScore(latestEntry);
        int weeklyAverage = ScoreEngine.averageScore(entries, 7);
        StringBuilder builder = new StringBuilder(buildTodayHeadline(latestScore));

        if (weeklyAverage > 0) {
            if (latestScore >= weeklyAverage + 8) {
                builder.append(" 최근 기준선보다 조금 나은 편입니다.");
            } else if (latestScore <= weeklyAverage - 8) {
                builder.append(" 최근 기준선보다 내려가 있어 기대치를 가볍게 두는 편이 맞습니다.");
            } else {
                builder.append(" 최근 기준선과 비슷하므로 강도보다 일관성이 더 중요합니다.");
            }
        }

        String noteSignal = detectNoteSignal(latestEntry.getNote());
        if (!noteSignal.isEmpty()) {
            builder.append(" ").append(noteSignal);
        }

        return builder.toString();
    }

    public static String buildTodayFocus(CheckInEntry latestEntry) {
        if (latestEntry.getStress() >= 8) {
            return "오늘의 초점: 일정 사이 완충 시간을 남기고 새로운 부담을 겹치지 마세요.";
        }
        if (latestEntry.getSleepQuality() <= 4) {
            return "오늘의 초점: 속도를 조금 낮추고 다음 휴식 구간을 먼저 지키세요.";
        }
        if (latestEntry.getEnergy() <= 4) {
            return "오늘의 초점: 모든 것을 따라잡으려 하기보다 의미 있는 한 가지를 끝내는 쪽이 낫습니다.";
        }
        if (latestEntry.getFocus() <= 4) {
            return "오늘의 초점: 일을 짧은 블록으로 나누고 매번 다음 한 단계만 선명하게 두세요.";
        }
        return "오늘의 초점: 이미 도움이 되는 리듬을 유지하고 불필요한 마찰을 늘리지 마세요.";
    }

    public static WeeklyReport buildWeeklyReport(List<CheckInEntry> entries) {
        if (entries.isEmpty()) {
            return new WeeklyReport(
                    "아직 주간 패턴이 보이지 않습니다.",
                    "체크인을 몇 번 더 쌓으면 로컬 주간 패턴을 만들 수 있습니다.",
                    "짧은 메모로 무엇이 도움 됐고 무엇이 소모됐는지 남겨 두세요.",
                    "첫 목표는 단순해야 합니다. 이 MVP에서는 양보다 일관성이 중요합니다.");
        }

        int averageScore = ScoreEngine.averageScore(entries, 7);
        int trendDelta = ScoreEngine.calculateTrendDelta(entries);
        int weeklyStress = ScoreEngine.averageStress(entries, 7);

        String headline;
        if (averageScore >= 70) {
            headline = "이번 주는 전반적으로 안정적이어서 평소 루틴을 유지할 여유가 있었습니다.";
        } else if (averageScore >= 45) {
            headline = "이번 주는 괜찮은 날과 걸리는 날이 섞여 있었습니다.";
        } else {
            headline = "이번 주는 전체적으로 무거웠습니다. 다음 주에는 회복 여지를 더 확보하는 편이 좋습니다.";
        }

        String pattern = "추세는 " + ScoreEngine.getTrendLabel(trendDelta) + "입니다. "
                + buildPatternLine(weeklyStress, entries.get(0).getNote());

        String reflection = "이번 주에 가장 크게 작용한 축은 "
                + determineStrongestLever(entries.get(0))
                + "으로 보입니다. 패턴을 잡으려면 메모를 짧고 구체적으로 남기는 편이 좋습니다.";

        String nextStep;
        if (trendDelta >= 8) {
            nextStep = "다음 한 걸음: 도움이 된 앵커를 그대로 유지하고 난이도를 급하게 올리지 마세요.";
        } else if (trendDelta <= -8) {
            nextStep = "다음 한 걸음: 반복되는 부담 하나를 줄이고 내일 계획을 의도적으로 더 작게 잡으세요.";
        } else {
            nextStep = "다음 한 걸음: 수면, 휴식, 작업 속도 중 하나를 정해 꾸준히 유지한 뒤 다음 주에 다시 보세요.";
        }

        return new WeeklyReport(headline, pattern, reflection, nextStep);
    }

    private static String buildPatternLine(int weeklyStress, String latestNote) {
        if (weeklyStress >= 7) {
            return "주간 스트레스가 높게 유지되어 가벼운 계획이 더 안전한 기본값입니다.";
        }
        if (weeklyStress <= 4) {
            return "주간 스트레스가 비교적 낮아 리듬을 유지하기 쉬운 편이었습니다.";
        }

        String noteSignal = detectNoteSignal(latestNote);
        if (!noteSignal.isEmpty()) {
            return noteSignal;
        }
        return "급격한 상승이나 하락은 크지 않아 보이며, 루틴의 영향이 크게 작동한 주였습니다.";
    }

    private static String determineStrongestLever(CheckInEntry latestEntry) {
        int bestValue = latestEntry.getSleepQuality();
        String bestLabel = "휴식 품질";

        if (latestEntry.getEnergy() > bestValue) {
            bestValue = latestEntry.getEnergy();
            bestLabel = "에너지 관리";
        }
        if (latestEntry.getFocus() > bestValue) {
            bestValue = latestEntry.getFocus();
            bestLabel = "집중 관리";
        }
        if ((10 - latestEntry.getStress()) > bestValue) {
            bestLabel = "부담 조절";
        }

        return bestLabel;
    }

    private static String detectNoteSignal(String note) {
        String normalized = note == null ? "" : note.trim().toLowerCase(Locale.KOREA);
        if (normalized.isEmpty()) {
            return "";
        }
        if (containsAny(normalized, "stress", "deadline", "busy", "overwhelmed", "스트레스", "마감", "바쁨", "과부하")) {
            return "메모상 업무 부담이나 압박이 주요 요인으로 보입니다.";
        }
        if (containsAny(normalized, "sleep", "late", "tired", "rest", "잠", "수면", "늦게", "피곤", "휴식")) {
            return "메모상 휴식 품질이 하루 흐름에 평소보다 크게 작용한 것으로 보입니다.";
        }
        if (containsAny(normalized, "walk", "exercise", "gym", "outside", "산책", "운동", "헬스", "밖")) {
            return "메모상 움직임이 기준선을 지탱하는 데 도움을 준 것으로 보입니다.";
        }
        if (containsAny(normalized, "calm", "steady", "good", "better", "차분", "안정", "좋", "나아")) {
            return "메모에 비교적 안정적인 하루가 드러나므로 무엇이 도움이 됐는지 반복해 볼 가치가 있습니다.";
        }
        return "메모가 중요한 맥락을 보태고 있으니, 힘든 날과 괜찮은 날의 차이를 계속 짧게 남겨 두세요.";
    }

    private static boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static final class WeeklyReport {
        private final String headline;
        private final String pattern;
        private final String reflection;
        private final String nextStep;

        public WeeklyReport(String headline, String pattern, String reflection, String nextStep) {
            this.headline = headline;
            this.pattern = pattern;
            this.reflection = reflection;
            this.nextStep = nextStep;
        }

        public String getHeadline() {
            return headline;
        }

        public String getPattern() {
            return pattern;
        }

        public String getReflection() {
            return reflection;
        }

        public String getNextStep() {
            return nextStep;
        }
    }
}
