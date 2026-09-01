package com.example.demo.application.dtos;

// Resumen "todo en uno" pensado para responder preguntas ricas del
// buscador asistido (Club de Beneficios) — cruza datos que hoy están
// repartidos en varios lugares (User, PointTransactionRepository) en
// una sola llamada.
public class PointsResumenDto {
    private int availablePoints;
    private int accumulatedPoints;
    private Integer monthlyCap;      // null = sin tope (Premium)
    private int earnedThisMonth;
    private boolean premium;
    private boolean creator;
    private String level;
    private String nextLevel;        // null = nivel máximo alcanzado
    private int pointsToNextLevel;
    private double progressToNextLevel;
    private int dailyCommentsUsed;
    private Integer dailyCommentsLimit;       // null = sin límite (Premium)
    private int dailyRecommendationsUsed;
    private Integer dailyRecommendationsLimit; // null = sin límite (Premium)
    private java.time.LocalDateTime nextExpirationDate; // null = Premium, o FREE sin lotes con vencimiento
    private Integer nextExpirationPoints;               // cuántos puntos vencen en esa fecha

    public int getAvailablePoints() { return availablePoints; }
    public void setAvailablePoints(int availablePoints) { this.availablePoints = availablePoints; }
    public int getAccumulatedPoints() { return accumulatedPoints; }
    public void setAccumulatedPoints(int accumulatedPoints) { this.accumulatedPoints = accumulatedPoints; }
    public Integer getMonthlyCap() { return monthlyCap; }
    public void setMonthlyCap(Integer monthlyCap) { this.monthlyCap = monthlyCap; }
    public int getEarnedThisMonth() { return earnedThisMonth; }
    public void setEarnedThisMonth(int earnedThisMonth) { this.earnedThisMonth = earnedThisMonth; }
    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
    public boolean isCreator() { return creator; }
    public void setCreator(boolean creator) { this.creator = creator; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getNextLevel() { return nextLevel; }
    public void setNextLevel(String nextLevel) { this.nextLevel = nextLevel; }
    public int getPointsToNextLevel() { return pointsToNextLevel; }
    public void setPointsToNextLevel(int pointsToNextLevel) { this.pointsToNextLevel = pointsToNextLevel; }
    public double getProgressToNextLevel() { return progressToNextLevel; }
    public void setProgressToNextLevel(double progressToNextLevel) { this.progressToNextLevel = progressToNextLevel; }
    public int getDailyCommentsUsed() { return dailyCommentsUsed; }
    public void setDailyCommentsUsed(int dailyCommentsUsed) { this.dailyCommentsUsed = dailyCommentsUsed; }
    public Integer getDailyCommentsLimit() { return dailyCommentsLimit; }
    public void setDailyCommentsLimit(Integer dailyCommentsLimit) { this.dailyCommentsLimit = dailyCommentsLimit; }
    public int getDailyRecommendationsUsed() { return dailyRecommendationsUsed; }
    public void setDailyRecommendationsUsed(int dailyRecommendationsUsed) { this.dailyRecommendationsUsed = dailyRecommendationsUsed; }
    public Integer getDailyRecommendationsLimit() { return dailyRecommendationsLimit; }
    public void setDailyRecommendationsLimit(Integer dailyRecommendationsLimit) { this.dailyRecommendationsLimit = dailyRecommendationsLimit; }
    public java.time.LocalDateTime getNextExpirationDate() { return nextExpirationDate; }
    public void setNextExpirationDate(java.time.LocalDateTime nextExpirationDate) { this.nextExpirationDate = nextExpirationDate; }
    public Integer getNextExpirationPoints() { return nextExpirationPoints; }
    public void setNextExpirationPoints(Integer nextExpirationPoints) { this.nextExpirationPoints = nextExpirationPoints; }
}