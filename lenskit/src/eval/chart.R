# Create a chart comparing the algorithms

library("ggplot2")

all.data <- read.csv("eval-user.csv")

serr = function(d) {qnorm(0.975)*sd(d)/sqrt(length(d))}

all.mean <- aggregate(cbind(RMSE, nDCG) ~ Algorithm+DataSet, data=all.data, mean)
all.serr <- aggregate(cbind(RMSE, nDCG) ~ Algorithm+DataSet, data=all.data, serr)
names(all.serr) <- c("Algorithm", "DataSet", "RMSEERR", "nDCGERR")
all.agg = merge(all.serr, all.mean)

rmse = ggplot(all.agg,aes(x=DataSet, y=RMSE, ymin=RMSE-RMSEERR, ymax=RMSE+RMSEERR, color=Algorithm))+geom_line()+geom_errorbar()
ndcg = ggplot(all.agg,aes(x=DataSet, y=nDCG, ymin=nDCG-nDCGERR, ymax=nDCG+nDCGERR, color=Algorithm))+geom_line()+geom_errorbar()

print("Outputting to rmse.pdf")
pdf("rmse.pdf", paper="letter", width=0, height=0)
print(rmse)
dev.off()

print("Outputting to ndcg.pdf")
pdf("ndcg.pdf", paper="letter", width=0, height=0)
print(ndcg)
dev.off()
