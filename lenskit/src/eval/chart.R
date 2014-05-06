# Create a chart comparing the algorithms

library("ggplot2")

all.data <- read.csv("eval-user.csv")

nonMetrics = c("Algorithm","Retain", "DataSet", "User", "Partition", "NGood", "TestEvents", "TrainEvents", "NAttempted", "RatingEntropy")

metrics = setdiff(names(all.data),nonMetrics)

for (metric in metrics) {
  plot = ggplot(all.data, aes_string(x="Retain", y=metric, color="Algorithm", shape="Algorithm"))+stat_summary(fun.y=mean, geom="line")+stat_summary(fun.y=mean, geom="point") + stat_summary(fun.data = mean_cl_normal, geom="errorbar")+scale_x_continuous(breaks=4*(0:5))
  metric = gsub('\\.', '_', metric)
  filename = paste(metric,".pdf",sep="")
  cat("Outputting to",filename,"\n")
  pdf(filename, width=4, height=3)
  print(plot)
  dev.off()
}


dat <- read.csv("eval-results.csv")
plot = ggplot(dat, aes(x=Retain, y=TopN.Entropy, color=Algorithm, shape=Algorithm))+geom_point()+stat_summary(fun.y=mean, geom="line")+ylab("Spread")+scale_x_continuous(breaks=4*(0:5))
pdf("topN_entropy.pdf", width=6, height=4)
print(plot)
dev.off()

library(reshape2)

all.data.melt = melt(all.data, c("Algorithm", "Retain", "User"), metrics)
melt.mean = aggregate(value~Algorithm+Retain+variable, data=all.data.melt, mean)
melt.var = aggregate(value~Algorithm+Retain+variable, data=all.data.melt, var)
names(melt.var)<- c("Algorithm","Retain","variable","var")
melt.length = aggregate(value~Algorithm+Retain+variable, data=all.data.melt, length)
names(melt.length)<- c("Algorithm","Retain","variable","length")
data.melt = merge(melt.var, merge(melt.length, melt.mean))

z = qnorm(1-0.05/2)

data.melt$cisize = sqrt(data.melt$var/data.melt$length)*z
data.melt$lb = data.melt$value-data.melt$cisize
data.melt$ub = data.melt$value+data.melt$cisize

# redo some names
data.melt$variable = as.character(data.melt$variable)
data.melt$variable[data.melt$variable == "MAP"] = "MAP@20"
data.melt$variable[data.melt$variable == "Precision"] = "Precision@20"
data.melt$variable[data.melt$variable == "Precision.fallout"] = "Fallout@20"
data.melt$variable[data.melt$variable == "TopN.RMSE.seenItems"] = "SeenItems@20"
data.melt$variable[data.melt$variable == "TopN.RMSE"] = "RMSE@20"
data.melt$variable[data.melt$variable == "TopN.Ave.Rat"] = "MeanRating@20"
data.melt$variable[data.melt$variable == "TopN.MeanPopularity"] = "AveragePopularity@20"
data.melt$variable[data.melt$variable == "diversity"] = "AILS@20"
data.melt$variable = ordered(data.melt$variable, levels = c("Coverage", "RMSE", "nDCG", "Precision@20", "MAP@20", "Fallout@20", "SeenItems@20", "MeanRating@20", "RMSE@20", "AveragePopularity@20", "AILS@20", "TestTime","MAE","MutualInformation","PredicitonEntropy","ItemScorer.NAttempted","ItemScorer.NGood","ItemScorer.Coverage","TopN.ActualLength", "Recall.fallout", "Recall"))

# make plots
vars = c("RMSE", "nDCG")
data.melt.sub = subset(data.melt, variable %in% vars)
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm, ymin=lb, ymax=ub))+geom_point()+geom_line()+geom_errorbar()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)
pdf("accuracy.pdf", width=8.5, height=3.5)
print(plot)
dev.off()


vars = c("Precision@20", "MAP@20", "Fallout@20")
data.melt.sub = subset(data.melt, variable %in% vars)
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm, ymin=lb, ymax=ub))+geom_point()+geom_line()+geom_errorbar()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)
pdf("TopNPrecision.pdf", width=8.5, height=3)
print(plot)
dev.off()


vars = c("SeenItems@20", "MeanRating@20", "RMSE@20")
data.melt.sub = subset(data.melt, variable %in% vars)
data.melt.sub = subset(data.melt.sub, Algorithm != "UserUser" | variable == "SeenItems@20")
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm, ymin=lb, ymax=ub))+geom_point()+geom_line()+geom_errorbar()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)
pdf("rmse_20.pdf", width=8.5, height=3)
print(plot)
dev.off()


vars = c("AveragePopularity@20", "AILS@20")
data.melt.sub = subset(data.melt, variable %in% vars)
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm, ymin=lb, ymax=ub))+geom_point()+geom_line()+geom_errorbar()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)
pdf("popdiv.pdf", width=8.5, height=3.5)
print(plot)
dev.off()
