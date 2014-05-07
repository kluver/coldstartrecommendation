# Create a chart comparing the algorithms

cp = c("#e41a1c","#377eb8","#4daf4a","#984ea3","#ff7f00")

library("ggplot2")
library(reshape2)

all.data = read.csv("eval-user.csv")

all.data.melt = melt(all.data, c("Algorithm", "Retain", "User"), metrics)
data.melt = aggregate(value~Algorithm+Retain+variable, data=all.data.melt, mean)

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

data.melt$Algorithm = ordered(data.melt$Algorithm, c("ItemItem", "UserUser", "svd", "ItemBaseline", "UserItemBaseline"))

# make plots
vars = c("RMSE", "nDCG")
data.melt.sub = subset(data.melt, variable %in% vars)
data.melt.sub = subset(data.melt.sub, Algorithm != "UserItemBaseline" | variable == "RMSE")
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm))+geom_point()+geom_line()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)+scale_color_manual(values=cp)+theme_minimal()+xlab("Simulated Profile Size")+theme(legend.position="bottom")
pdf("accuracy.pdf", width=8.5*2/2.5, height=3.5)
print(plot)
dev.off()


vars = c("Precision@20", "MAP@20", "Fallout@20")
data.melt.sub = subset(data.melt, variable %in% vars)
data.melt.sub = subset(data.melt.sub, Algorithm != "UserItemBaseline")
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm))+geom_point()+geom_line()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)+scale_color_manual(values=cp)+theme_minimal()+xlab("Simulated Profile Size")+theme(legend.position="bottom")
pdf("TopNPrecision.pdf", width=8.5, height=3.5)
print(plot)
dev.off()

vars = c("SeenItems@20", "MeanRating@20", "RMSE@20")
data.melt.sub = subset(data.melt, variable %in% vars)
data.melt.sub = subset(data.melt.sub, Algorithm != "UserUser" | variable == "SeenItems@20")
data.melt.sub = subset(data.melt.sub, Algorithm != "UserItemBaseline" | variable == "RMSE@20")

plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm))+geom_point()+geom_line()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)+scale_color_manual(values=cp)+theme_minimal()+xlab("Simulated Profile Size")+theme(legend.position="bottom")
pdf("rmse_20.pdf", width=8.5, height=3.5)
print(plot)
dev.off()

spread.dat <- read.csv("eval-results.csv")
spread.dat = spread.dat[c("Algorithm","Retain", "TopN.Entropy", "Partition")]
spread.mean = aggregate(TopN.Entropy~Algorithm+Retain, data=spread.dat, mean)
names(spread.mean) <- c("Algorithm", "Retain", "value")
spread.mean$variable = "Spread@20"

vars = c("AveragePopularity@20", "AILS@20")
data.melt.sub = subset(data.melt, variable %in% vars)
data.melt.sub = rbind(data.melt.sub, spread.mean)
data.melt.sub$variable = ordered(data.melt.sub$variable, levels = c("AveragePopularity@20", "AILS@20", "Spread@20"))

data.melt.sub = subset(data.melt.sub, Algorithm != "UserItemBaseline")
plot = ggplot(data.melt.sub, aes(x=Retain, y=value, color=Algorithm, shape=Algorithm))+geom_point()+geom_line()+scale_x_continuous(breaks=4*(0:5))+facet_wrap(~variable, scales="free_y", nrow=1)+scale_color_manual(values=cp)+theme_minimal()+xlab("Simulated Profile Size")+theme(legend.position="bottom")
pdf("popdiv.pdf", width=8.5, height=3.5)
print(plot)
dev.off()

