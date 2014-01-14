# Create a chart comparing the algorithms

#cbbPalette <- c("#000000", "#E69F00", "#56B4E9", "#009E73", "#F0E442", "#0072B2", "#D55E00", "#CC79A7")

library("ggplot2")

all.data <- read.csv("eval-user.csv")

serr = function(d) {qnorm(0.975)*sd(d)/sqrt(length(d))}

all.mean <- aggregate(cbind(RMSE, MAE, nDCG, Information.ByUser) ~ Algorithm+DataSet, data=all.data, mean)
all.serr <- aggregate(cbind(RMSE, MAE, nDCG, Information.ByUser) ~ Algorithm+DataSet, data=all.data, serr)
names(all.serr) <- c("Algorithm", "DataSet", "RMSEE", "MAEE", "nDCGE", "Information.ByUserE")
all.agg = merge(all.mean, all.serr)

rmse = ggplot(all.agg,aes(x=DataSet, y=RMSE, ymin=RMSE-RMSEE, ymax=RMSE+RMSEE, color=Algorithm))+geom_line()+geom_errorbar()

rmse
print("Outputting to rmse.pdf")
pdf("rmse.pdf", paper="letter", width=0, height=0)
print(rmse)
dev.off()

mae = ggplot(all.agg,aes(x=DataSet, y=MAE, ymin = MAE-MAEE, ymax=MAE+MAEE, color=Algorithm))+geom_line()+geom_errorbar()

print("Outputting to mae.pdf")
pdf("mae.pdf", paper="letter", width=0, height=0)
print(mae)
dev.off()

ndcg = ggplot(all.agg,aes(x=DataSet, y=nDCG, ymin=nDCG-nDCGE, ymax=nDCG+nDCGE, color=Algorithm))+geom_line()+geom_errorbar()

print("Outputting to ndcg.pdf")
pdf("ndcg.pdf", paper="letter", width=0, height=0)
print(ndcg)
dev.off()

info = ggplot(all.agg,aes(x=DataSet, y=Information.ByUser, ymin=Information.ByUser-Information.ByUserE, ymax =Information.ByUser+Information.ByUserE, color=Algorithm))+geom_line()+geom_errorbar()

print("Outputting to info.pdf")
pdf("info.pdf", paper="letter", width=0, height=0)
print(info)
dev.off()