# Create a chart comparing the algorithms

cbbPalette <- c("#000000", "#E69F00", "#56B4E9", "#009E73", "#F0E442", "#0072B2", "#D55E00", "#CC79A7")
library("ggplot2")

all.data <- read.csv("eval-user.csv")

serr = function(d) {qnorm(0.975)*sd(d)/sqrt(length(d))}

for (metric in c("Coverage","RMSE","MAE","nDCG","Information.ByUser", "TopN.nDCG", "TopN.ActualLength", "TopN.avgPop", "TopN.diversity", "recall.100", "recall.500", "recall.1000", "fallout","recall.allTest")) {
  plot = ggplot(all.data, aes_string(x="DataSet", y=metric, color="Algorithm"))+stat_summary(fun.y=mean, geom="line") + stat_summary(fun.data = mean_cl_boot, geom="errorbar")
  filename = paste(metric,".pdf",sep="")
  cat("Outputting to",filename,"\n")
  pdf(filename, width=11, height=8.5)
  print(plot)
  dev.off()
}


dat <- read.csv("eval-results.csv")
plot = ggplot(dat, aes(x=DataSet, y=TopN.pop.entropy, color=Algorithm))+geom_point()+stat_summary(fun.y=mean, geom="line")
pdf("topN.pop.entropy.pdf", width=11, height=8.5)
print(plot)
dev.off()